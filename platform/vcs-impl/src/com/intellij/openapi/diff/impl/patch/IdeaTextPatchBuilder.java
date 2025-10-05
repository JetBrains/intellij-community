// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.diff.DiffContentFactoryImpl;
import com.intellij.diff.util.Side;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.ex.PartialCommitHelper;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
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
    return TextPatchBuilder.buildPatch(revisions, basePath, reversePatch);
  }

  /**
   * @deprecated Use {@link #buildPatch}
   */
  @Deprecated
  public static @NotNull List<FilePatch> buildPatch(@Nullable Project project,
                                                    @NotNull Collection<? extends Change> changes,
                                                    @NotNull Path basePath,
                                                    boolean reversePatch,
                                                    boolean honorExcludedFromCommit,
                                                    @Nullable Runnable ignoredParameter) throws VcsException {
    return buildPatch(project, changes, basePath, reversePatch, honorExcludedFromCommit);
  }

  private static @Nullable AirContentRevision convertRevision(@Nullable ContentRevision cr) {
    return convertRevision(cr, null);
  }

  public static boolean isBinaryRevision(@Nullable ContentRevision cr) {
    if (cr == null) return false;
    if (cr instanceof BinaryContentRevision) return true;

    FilePath file = cr.getFile();
    FileType type = file.getFileType();
    if (type instanceof UnknownFileType && cr instanceof ByteBackedContentRevision byteBasedContentRevision) {
      try {
        byte[] bytes = byteBasedContentRevision.getContentAsBytes();
        if (bytes != null) {
          return DiffContentFactoryImpl.isBinaryContent(bytes, type);
        }
      }
      catch (VcsException ignored) {
      }
    }
    return type.isBinary();
  }

  private static @Nullable AirContentRevision convertRevision(@Nullable ContentRevision cr, @Nullable String actualTextContent) {
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

  private static @Nullable Long getRevisionTimestamp(@NotNull ContentRevision revision) {
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
    private final @NotNull ByteBackedContentRevision myRevision;
    private final @NotNull FilePath myFilePath;

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
    public @NotNull String getContentAsString() {
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
    public @NotNull FilePath getPath() {
      return myFilePath;
    }
  }

  private static class TextAirContentRevision implements AirContentRevision {
    private final @NotNull ContentRevision myRevision;
    private final @NotNull FilePath myFilePath;

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
    public @NotNull String getContentAsString() throws VcsException {
      String content = myRevision.getContent();
      if (content == null) {
        VcsRevisionNumber revisionNumber = myRevision.getRevisionNumber();
        String revisionText = revisionNumber != VcsRevisionNumber.NULL ? revisionNumber.asString() : myRevision.toString();
        throw new VcsException(VcsBundle.message("patch.failed.to.fetch.old.content.for.file.name.in.revision",
                                                 myFilePath.getPath(), revisionText));
      }
      return content;
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
    public @NotNull FilePath getPath() {
      return myFilePath;
    }

    @Override
    public @NotNull Charset getCharset() {
      return myRevision.getFile().getCharset();
    }

    @Override
    public @Nullable String getLineSeparator() {
      VirtualFile virtualFile = myRevision.getFile().getVirtualFile();
      return virtualFile != null ? virtualFile.getDetectedLineSeparator() : null;
    }
  }

  private static class PartialTextAirContentRevision extends TextAirContentRevision {
    private final @NotNull String myContent;

    PartialTextAirContentRevision(@NotNull String content,
                                  @NotNull ContentRevision delegateRevision,
                                  @NotNull FilePath filePath) {
      super(delegateRevision, filePath);
      myContent = content;
    }

    @Override
    public @NotNull String getContentAsString() {
      return myContent;
    }

    @Override
    public byte[] getContentAsBytes() {
      return myContent.getBytes(getCharset());
    }
  }
}
