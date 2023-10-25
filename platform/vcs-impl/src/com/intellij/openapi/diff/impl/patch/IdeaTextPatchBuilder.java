// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.diff.util.Side;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.ex.PartialCommitHelper;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class IdeaTextPatchBuilder {
  private static final Logger LOG = Logger.getInstance(IdeaTextPatchBuilder.class);

  private IdeaTextPatchBuilder() {
  }

  private static List<BeforeAfter<AirContentRevision>> revisionsConvertor(@NotNull Project project,
                                                                          @NotNull List<? extends Change> changes,
                                                                          boolean honorExcludedFromCommit) {
    final List<BeforeAfter<AirContentRevision>> result = new ArrayList<>(changes.size());
    addConvertChanges(project, changes, result, honorExcludedFromCommit);
    return result;
  }

  private static void addConvertChanges(@NotNull Project project,
                                        @NotNull Collection<? extends Change> changes,
                                        @NotNull List<? super BeforeAfter<AirContentRevision>> result,
                                        boolean honorExcludedFromCommit) {
    Collection<Change> otherChanges = PartialChangesUtil.processPartialChanges(project, changes, false, (partialChanges, tracker) -> {
      if (!tracker.hasPartialChangesToCommit()) return false;
      if (!tracker.isOperational()) {
        LOG.warn("Skipping non-operational tracker: " + tracker);
        return false;
      }

      List<String> changelistIds = ContainerUtil.map(partialChanges, ChangeListChange::getChangeListId);
      Change change = partialChanges.get(0).getChange();

      PartialCommitHelper helper = tracker.handlePartialCommit(Side.LEFT, changelistIds, honorExcludedFromCommit);
      String actualText = helper.getContent();

      result.add(new BeforeAfter<>(convertRevision(change.getBeforeRevision(), null),
                                   convertRevision(change.getAfterRevision(), actualText)));
      return true;
    });

    for (Change change : otherChanges) {
      result.add(new BeforeAfter<>(convertRevision(change.getBeforeRevision(), null),
                                   convertRevision(change.getAfterRevision(), null)));
    }
  }

  public static @NotNull List<FilePatch> buildPatch(Project project,
                                                    @NotNull Collection<? extends Change> changes,
                                                    @NotNull String basePath,
                                                    boolean reversePatch) throws VcsException {
    return buildPatch(project, changes, Paths.get(basePath), reversePatch, false);
  }

  public static @NotNull List<FilePatch> buildPatch(Project project,
                                                    @NotNull Collection<? extends Change> changes,
                                                    @NotNull Path basePath,
                                                    boolean reversePatch) throws VcsException {
    return buildPatch(project, changes, basePath, reversePatch, false);
  }

  public static @NotNull List<FilePatch> buildPatch(@Nullable Project project,
                                                    @NotNull Collection<? extends Change> changes,
                                                    @NotNull Path basePath,
                                                    boolean reversePatch,
                                                    boolean honorExcludedFromCommit) throws VcsException {
    Collection<BeforeAfter<AirContentRevision>> revisions;
    if (project != null) {
      revisions = revisionsConvertor(project, new ArrayList<>(changes), honorExcludedFromCommit);
    }
    else {
      revisions = new ArrayList<>(changes.size());
      for (Change change : changes) {
        revisions.add(new BeforeAfter<>(convertRevision(change.getBeforeRevision()),
                                        convertRevision(change.getAfterRevision())));
      }
    }
    return TextPatchBuilder.buildPatch(revisions, basePath, reversePatch, () -> ProgressManager.checkCanceled());
  }

  @Nullable
  private static AirContentRevision convertRevision(@Nullable ContentRevision cr) {
    return convertRevision(cr, null);
  }

  public static boolean isBinaryRevision(@Nullable ContentRevision cr) {
    if (cr == null) return false;
    if (cr instanceof BinaryContentRevision) return true;
    return cr.getFile().getFileType().isBinary();
  }

  @Nullable
  private static AirContentRevision convertRevision(@Nullable ContentRevision cr, @Nullable String actualTextContent) {
    if (cr == null) {
      return null;
    }

    FilePath filePath = cr.getFile();
    if (actualTextContent != null) {
      return new PartialTextAirContentRevision(actualTextContent, cr, filePath);
    }
    else if (cr instanceof ByteBackedContentRevision &&
             isBinaryRevision(cr)) {
      return new BinaryAirContentRevision((ByteBackedContentRevision)cr, filePath);
    }
    else {
      return new TextAirContentRevision(cr, filePath);
    }
  }

  @Nullable
  private static Long getRevisionTimestamp(@NotNull ContentRevision revision) {
    if (revision instanceof CurrentContentRevision) {
      try {
        FilePath filePath = revision.getFile();
        Path path = filePath.getIOFile().toPath();
        return Files.getLastModifiedTime(path).toMillis();
      }
      catch (IOException e) {
        return null;
      }
    }
    return null;
  }


  private static class BinaryAirContentRevision implements AirContentRevision {
    @NotNull private final ByteBackedContentRevision myRevision;
    @NotNull private final FilePath myFilePath;

    BinaryAirContentRevision(@NotNull ByteBackedContentRevision revision,
                             @NotNull FilePath filePath) {
      myRevision = revision;
      myFilePath = filePath;
    }

    @Override
    public boolean isBinary() {
      return true;
    }

    @Override
    public String getContentAsString() {
      throw new IllegalStateException();
    }

    @Override
    public byte[] getContentAsBytes() throws VcsException {
      return myRevision.getContentAsBytes();
    }

    @Override
    public String getRevisionNumber() {
      return myRevision.getRevisionNumber().asString();
    }

    @Override
    public @Nullable Long getLastModifiedTimestamp() {
      return getRevisionTimestamp(myRevision);
    }

    @Override
    @NotNull
    public FilePath getPath() {
      return myFilePath;
    }
  }

  private static class TextAirContentRevision implements AirContentRevision {
    @NotNull private final ContentRevision myRevision;
    @NotNull private final FilePath myFilePath;

    TextAirContentRevision(@NotNull ContentRevision revision,
                           @NotNull FilePath filePath) {
      myRevision = revision;
      myFilePath = filePath;
    }

    @Override
    public boolean isBinary() {
      return false;
    }

    @Override
    public String getContentAsString() throws VcsException {
      return myRevision.getContent();
    }

    @Override
    public byte[] getContentAsBytes() throws VcsException {
      return ChangesUtil.loadContentRevision(myRevision);
    }

    @Override
    public String getRevisionNumber() {
      return myRevision.getRevisionNumber().asString();
    }

    @Override
    public @Nullable Long getLastModifiedTimestamp() {
      return getRevisionTimestamp(myRevision);
    }

    @Override
    @NotNull
    public FilePath getPath() {
      return myFilePath;
    }

    @NotNull
    @Override
    public Charset getCharset() {
      return myRevision.getFile().getCharset();
    }

    @Nullable
    @Override
    public String getLineSeparator() {
      VirtualFile virtualFile = myRevision.getFile().getVirtualFile();
      return virtualFile != null ? virtualFile.getDetectedLineSeparator() : null;
    }
  }

  private static class PartialTextAirContentRevision extends TextAirContentRevision {
    @NotNull private final String myContent;

    PartialTextAirContentRevision(@NotNull String content,
                                  @NotNull ContentRevision delegateRevision,
                                  @NotNull FilePath filePath) {
      super(delegateRevision, filePath);
      myContent = content;
    }

    @Override
    public String getContentAsString() {
      return myContent;
    }

    @Override
    public byte[] getContentAsBytes() {
      return myContent.getBytes(getCharset());
    }
  }
}
