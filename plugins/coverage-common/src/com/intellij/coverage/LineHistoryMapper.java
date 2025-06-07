// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.vcsUtil.VcsUtil;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.Date;
import java.util.List;

/**
 * This service finds correspondence of lines of a file in a specified timestamp in VCS/local history and current state of the file.
 */
final class LineHistoryMapper {
  private static final Logger LOG = Logger.getInstance(LineHistoryMapper.class);
  private final Object myLock = new Object();
  private final Project myProject;
  private final VirtualFile myFile;
  private final Document myDocument;
  private final long myDate;
  private SoftReference<Int2IntMap> myNewToOldLines;
  private SoftReference<Int2IntMap> myOldToNewLines;
  private volatile SoftReference<byte[]> myOldContent;

  LineHistoryMapper(Project project, VirtualFile file, Document document, long date) {
    myProject = project;
    myFile = file;
    myDocument = document;
    myDate = date;
  }

  public long getTimeStamp() {
    return myDate;
  }

  public void clear() {
    myNewToOldLines = null;
    myOldToNewLines = null;
  }

  /**
   * @return true when mapping can be built without time-consuming history search
   */
  public boolean canGetFastMapping() {
    return myOldContent != null;
  }

  public @Nullable Int2IntMap getOldToNewLineMapping() {
    if (myOldToNewLines == null) {
      myOldToNewLines = doGetLineMapping(true);
      if (myOldToNewLines == null) return null;
    }
    return myOldToNewLines.get();
  }

  public @Nullable Int2IntMap getNewToOldLineMapping() {
    if (myNewToOldLines == null) {
      myNewToOldLines = doGetLineMapping(false);
      if (myNewToOldLines == null) return null;
    }
    return myNewToOldLines.get();
  }

  /**
   * Calculate lines mapping.
   *
   * @param oldToNew a flag to calculate mapping from old to new, or new to old otherwise
   * @return null if processing failed, null soft reference if it is impossible to calculate mapping,
   * or soft reference to the result in case of success
   */
  private @Nullable SoftReference<Int2IntMap> doGetLineMapping(boolean oldToNew) {
    if (myOldContent == null && ApplicationManager.getApplication().isDispatchThread()) return null;
    final byte[] oldContent;
    synchronized (myLock) {
      if (myOldContent == null) {
        byte[] byteContent = loadFromLocalHistory();

        if (byteContent == null && myFile.getTimeStamp() > myDate) {
          byteContent = loadFromVersionControl();
        }
        myOldContent = new SoftReference<>(byteContent);
      }
      oldContent = myOldContent.get();
    }

    if (oldContent == null) return new SoftReference<>(null);
    String[] historyLines = getLinesFromBytes(oldContent);
    String[] currentLines = getUpToDateLines();

    String[] oldLines = oldToNew ? historyLines : currentLines;
    String[] newLines = oldToNew ? currentLines : historyLines;

    Diff.Change change;
    try {
      change = Diff.buildChanges(oldLines, newLines);
    }
    catch (FilesTooBigForDiffException e) {
      LOG.info(e);
      return new SoftReference<>(null);
    }
    return new SoftReference<>(buildMapping(change, oldLines.length));
  }

  private byte @Nullable [] loadFromLocalHistory() {
    return LocalHistory.getInstance().getByteContent(myFile, (t) -> t < myDate);
  }

  private byte @Nullable [] loadFromVersionControl() {
    try {
      final AbstractVcs vcs = VcsUtil.getVcsFor(myProject, myFile);
      if (vcs == null) return null;

      final VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
      if (historyProvider == null) return null;

      final FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(myFile);
      final VcsHistorySession session = historyProvider.createSessionFor(filePath);
      if (session == null) return null;

      final List<VcsFileRevision> list = session.getRevisionList();
      if (list == null) return null;

      for (VcsFileRevision revision : list) {
        final Date revisionDate = revision.getRevisionDate();
        if (revisionDate == null) return null;

        if (revisionDate.getTime() < myDate) {
          return revision.loadContent();
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return null;
  }


  private String @NotNull [] getLinesFromBytes(byte @NotNull [] oldContent) {
    try (AccessToken ignore = ProjectLocator.withPreferredProject(myFile, myProject)) {
      String text = LoadTextUtil.getTextByBinaryPresentation(oldContent, myFile, false, false).toString();
      return LineTokenizer.tokenize(text, false);
    }
  }

  private String @NotNull [] getUpToDateLines() {
    return ReadAction.compute(() -> {
      final int lineCount = myDocument.getLineCount();
      final String[] lines = new String[lineCount];
      final CharSequence chars = myDocument.getCharsSequence();
      for (int i = 0; i < lineCount; i++) {
        lines[i] = chars.subSequence(myDocument.getLineStartOffset(i), myDocument.getLineEndOffset(i)).toString();
      }
      return lines;
    });
  }

  private static Int2IntMap buildMapping(Diff.Change change, int firstNLines) {
    Int2IntMap result = new Int2IntOpenHashMap();
    int prevLineInFirst = 0;
    int prevLineInSecond = 0;
    while (change != null) {
      for (int l = 0; l < change.line0 - prevLineInFirst; l++) {
        result.put(prevLineInFirst + l, prevLineInSecond + l);
      }

      prevLineInFirst = change.line0 + change.deleted;
      prevLineInSecond = change.line1 + change.inserted;

      change = change.link;
    }

    for (int i = prevLineInFirst; i < firstNLines; i++) {
      result.put(i, prevLineInSecond + i - prevLineInFirst);
    }

    return result;
  }
}
