// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.diff.util.Side;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsOutgoingChangesProvider;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.ex.PartialCommitHelper;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BeforeAfter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.*;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterPath;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getBeforePath;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.vcsUtil.VcsUtil.groupByRoots;

public class IdeaTextPatchBuilder {
  private IdeaTextPatchBuilder() {
  }

  private static List<BeforeAfter<AirContentRevision>> revisionsConvertor(@NotNull Project project,
                                                                          @NotNull List<? extends Change> changes,
                                                                          boolean honorExcludedFromCommit) throws VcsException {
    final List<BeforeAfter<AirContentRevision>> result = new ArrayList<>(changes.size());
    Map<VcsRoot, List<Change>> byRoots =
      groupByRoots(project, changes, change -> chooseNotNull(getBeforePath(change), getAfterPath(change)));

    for (VcsRoot root : byRoots.keySet()) {
      final Collection<Change> rootChanges = byRoots.get(root);

      if (root.getVcs() == null || root.getVcs().getOutgoingChangesProvider() == null) {
        addConvertChanges(project, rootChanges, result, null, honorExcludedFromCommit);
      }
      else {
        final VcsOutgoingChangesProvider<?> provider = root.getVcs().getOutgoingChangesProvider();
        final Collection<Change> basedOnLocal = provider.filterLocalChangesBasedOnLocalCommits(rootChanges, root.getPath());
        rootChanges.removeAll(basedOnLocal);

        addConvertChanges(project, rootChanges, result, null, honorExcludedFromCommit);
        addConvertChanges(project, basedOnLocal, result, provider, honorExcludedFromCommit);
      }
    }
    return result;
  }

  private static void addConvertChanges(@NotNull Project project,
                                        @NotNull Collection<? extends Change> changes,
                                        @NotNull List<? super BeforeAfter<AirContentRevision>> result,
                                        @Nullable VcsOutgoingChangesProvider<?> provider,
                                        boolean honorExcludedFromCommit) {
    Collection<Change> otherChanges = PartialChangesUtil.processPartialChanges(project, changes, false, (partialChanges, tracker) -> {
      if (!tracker.hasPartialChangesToCommit()) return false;

      List<String> changelistIds = ContainerUtil.map(partialChanges, ChangeListChange::getChangeListId);
      Change change = partialChanges.get(0).getChange();

      PartialCommitHelper helper = tracker.handlePartialCommit(Side.LEFT, changelistIds, honorExcludedFromCommit);
      String actualText = helper.getContent();

      result.add(new BeforeAfter<>(convertRevision(change.getBeforeRevision(), null, provider),
                                   convertRevision(change.getAfterRevision(), actualText, provider)));
      return true;
    });

    for (Change change : otherChanges) {
      result.add(new BeforeAfter<>(convertRevision(change.getBeforeRevision(), null, provider),
                                   convertRevision(change.getAfterRevision(), null, provider)));
    }
  }

  @NotNull
  public static List<FilePatch> buildPatch(Project project,
                                           Collection<? extends Change> changes,
                                           String basePath,
                                           boolean reversePatch) throws VcsException {
    return buildPatch(project, changes, basePath, reversePatch, false);
  }

  @NotNull
  public static List<FilePatch> buildPatch(Project project,
                                           Collection<? extends Change> changes,
                                           String basePath,
                                           boolean reversePatch,
                                           boolean honorExcludedFromCommit) throws VcsException {
    final Collection<BeforeAfter<AirContentRevision>> revisions;
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
    return TextPatchBuilder.buildPatch(revisions, basePath, reversePatch, SystemInfo.isFileSystemCaseSensitive,
                                       () -> ProgressManager.checkCanceled());
  }

  @Nullable
  private static AirContentRevision convertRevision(@Nullable ContentRevision cr) {
    return convertRevision(cr, null, null);
  }

  @Nullable
  private static AirContentRevision convertRevision(@Nullable ContentRevision cr,
                                                    @Nullable String actualTextContent,
                                                    @Nullable VcsOutgoingChangesProvider provider) {
    if (cr == null) return null;
    if (provider != null) {
      final Date date = provider.getRevisionDate(cr.getRevisionNumber(), cr.getFile());
      final Long ts = date == null ? null : date.getTime();
      return convertRevisionToAir(cr, actualTextContent, ts);
    }
    else {
      return convertRevisionToAir(cr, actualTextContent, null);
    }
  }

  @NotNull
  private static AirContentRevision convertRevisionToAir(@NotNull ContentRevision cr,
                                                         @Nullable String actualTextContent,
                                                         @Nullable Long ts) {
    final FilePath fp = cr.getFile();
    final StaticPathDescription description = new StaticPathDescription(fp.isDirectory(),
                                                                        ts == null ? fp.getIOFile().lastModified() : ts, fp.getPath());

    if (actualTextContent != null) {
      return new PartialTextAirContentRevision(actualTextContent, cr, description, ts);
    }
    else if (cr instanceof BinaryContentRevision) {
      return new BinaryAirContentRevision((BinaryContentRevision)cr, description, ts);
    }
    else {
      return new TextAirContentRevision(cr, description, ts);
    }
  }

  private static class BinaryAirContentRevision implements AirContentRevision {
    @NotNull private final BinaryContentRevision myRevision;
    @NotNull private final StaticPathDescription myDescription;
    @Nullable private final Long myTimestamp;

    BinaryAirContentRevision(@NotNull BinaryContentRevision revision,
                                    @NotNull StaticPathDescription description,
                                    @Nullable Long timestamp) {
      myRevision = revision;
      myDescription = description;
      myTimestamp = timestamp;
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
      return myRevision.getBinaryContent();
    }

    @Override
    public String getRevisionNumber() {
      return myTimestamp != null ? null : myRevision.getRevisionNumber().asString();
    }

    @Override
    @NotNull
    public PathDescription getPath() {
      return myDescription;
    }
  }

  private static class TextAirContentRevision implements AirContentRevision {
    @NotNull private final ContentRevision myRevision;
    @NotNull private final StaticPathDescription myDescription;
    @Nullable private final Long myTimestamp;

    TextAirContentRevision(@NotNull ContentRevision revision,
                                  @NotNull StaticPathDescription description,
                                  @Nullable Long timestamp) {
      myRevision = revision;
      myDescription = description;
      myTimestamp = timestamp;
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
      if (myRevision instanceof ByteBackedContentRevision) {
        return ((ByteBackedContentRevision)myRevision).getContentAsBytes();
      }

      String textContent = getContentAsString();
      if (textContent == null) return null;
      return textContent.getBytes(getCharset());
    }

    @Override
    public String getRevisionNumber() {
      return myTimestamp != null ? null : myRevision.getRevisionNumber().asString();
    }

    @Override
    @NotNull
    public PathDescription getPath() {
      return myDescription;
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
                                         @NotNull StaticPathDescription description,
                                         @Nullable Long timestamp) {
      super(delegateRevision, description, timestamp);
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
