/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.coverage;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftReference;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.SourceLineCounter;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.LightColors;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.diff.Diff;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author ven
 */
public class SrcFileAnnotator implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.coverage.SrcFileAnnotator");
  public static final Key<List<RangeHighlighter>> COVERAGE_HIGHLIGHTERS = Key.create("COVERAGE_HIGHLIGHTERS");
  private static final Key<DocumentListener> COVERAGE_DOCUMENT_LISTENER = Key.create("COVERAGE_DOCUMENT_LISTENER");
  public static final Key<EditorNotificationPanel> NOTIFICATION_PANEL = Key.create("NOTIFICATION_PANEL");

  private final PsiFile myFile;
  private final Editor myEditor;
  private final Document myDocument;
  private final Project myProject;
  private static final Icon ourIcon = IconLoader.getIcon("/general/exclMark.png");

  private SoftReference<TIntIntHashMap> myNewToOldLines;
  private SoftReference<TIntIntHashMap> myOldToNewLines;


  public SrcFileAnnotator(final PsiFile file, final Editor editor) {
    myFile = file;
    myEditor = editor;
    myProject = file.getProject();
    myDocument = myEditor.getDocument();
  }

  public void hideCoverageData() {
    MarkupModel markupModel = myDocument.getMarkupModel(myProject);
    final List<RangeHighlighter> highlighters = myFile.getUserData(COVERAGE_HIGHLIGHTERS);
    if (highlighters != null) {
      for (RangeHighlighter highlighter : highlighters) {
        markupModel.removeHighlighter(highlighter);
      }
      myFile.putUserData(COVERAGE_HIGHLIGHTERS, null);
    }
    else {
      final EditorNotificationPanel panel = myFile.getUserData(NOTIFICATION_PANEL);
      if (panel != null) {
        myFile.putUserData(NOTIFICATION_PANEL, null);
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        final VirtualFile vFile = myFile.getVirtualFile();
        assert vFile != null;
        for (final FileEditor editor : fileEditorManager.getEditors(vFile)) {
          fileEditorManager.removeTopComponent(editor, panel);
        }
      }
    }
    final DocumentListener documentListener = myFile.getUserData(COVERAGE_DOCUMENT_LISTENER);
    if (documentListener != null) {
      myDocument.removeDocumentListener(documentListener);
      myFile.putUserData(COVERAGE_DOCUMENT_LISTENER, null);
    }
  }

  private static
  @NotNull
  String[] getCoveredLines(@NotNull byte[] oldContent, VirtualFile vFile) {
    final String text = LoadTextUtil.getTextByBinaryPresentation(oldContent, vFile, false).toString();
    return LineTokenizer.tokenize(text, false);
  }

  private
  @NotNull
  String[] getUpToDateLines() {
    final int lineCount = myDocument.getLineCount();
    String[] lines = new String[lineCount];
    final CharSequence chars = myDocument.getCharsSequence();
    for (int i = 0; i < lineCount; i++) {
      lines[i] = chars.subSequence(myDocument.getLineStartOffset(i), myDocument.getLineEndOffset(i)).toString();
    }

    return lines;
  }

  private static TIntIntHashMap getCoverageVersionToCurrentLineMapping(Diff.Change change, int firstNLines) {
    TIntIntHashMap result = new TIntIntHashMap();
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

  @Nullable
  private TIntIntHashMap getOldToNewLineMapping(final long date) {
    if (myOldToNewLines == null) {
      myOldToNewLines = doGetLineMapping(date, true);
      if (myOldToNewLines == null) return null;
    }
    return myOldToNewLines.get();
  }

  @Nullable
  private TIntIntHashMap getNewToOldLineMapping(final long date) {
    if (myNewToOldLines == null) {
      myNewToOldLines = doGetLineMapping(date, false);
      if (myNewToOldLines == null) return null;
    }
    return myNewToOldLines.get();
  }

  @Nullable
  private SoftReference<TIntIntHashMap> doGetLineMapping(final long date, boolean oldToNew) {
    VirtualFile f = getVirtualFile();

    byte[] oldContent = LocalHistory.getInstance().getByteContent(f, new FileRevisionTimestampComparator() {
      public boolean isSuitable(long revisionTimestamp) {
        return revisionTimestamp < date;
      }
    });
    if (oldContent == null) return null;

    String[] coveredLines = getCoveredLines(oldContent, f);
    String[] currentLines = getUpToDateLines();

    String[] oldLines = oldToNew ? coveredLines : currentLines;
    String[] newLines = oldToNew ? currentLines : coveredLines;

    Diff.Change change = Diff.buildChanges(oldLines, newLines);
    return new SoftReference<TIntIntHashMap>(getCoverageVersionToCurrentLineMapping(change, oldLines.length));
  }

  public void showCoverageInformation(final CoverageSuite suite) {
    final MarkupModel markupModel = myDocument.getMarkupModel(myProject);
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    final ProjectData data = suite.getCoverageData(CoverageDataManager.getInstance(myProject));
    if (data == null) {
      coverageDataNotFound(suite);
      return;
    }

    final TIntIntHashMap oldToNewLineMapping = getOldToNewLineMapping(suite.getLastCoverageTimeStamp());
    if (oldToNewLineMapping == null) {
      showEditorWarningMessage(CodeInsightBundle.message("coverage.data.outdated"));
      return;
    }


    final Module module = ModuleUtil.findModuleForPsiElement(myFile);
    if (module == null) return;

    final VirtualFile outputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    final VirtualFile testOutputpath = CompilerModuleExtension.getInstance(module).getCompilerOutputPathForTests();

    if (outputpath == null || (suite.isTrackTestFolders() && testOutputpath == null)) {
      if (Messages.showOkCancelDialog(
        "Project class files are out of date. Would you like to recompile? The refusal to do it will result in incomplete coverage information",
        "Project is out of date", Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
        final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
        compilerManager.make(compilerManager.createProjectCompileScope(myProject), new CompileStatusNotification() {
          public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
            if (aborted || errors != 0) return;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                CoverageDataManager.getInstance(myProject).chooseSuite(suite);
              }
            });
          }
        });
      }
      return;
    }

    final String packageFQName = ((PsiClassOwner)myFile).getPackageName();
    final String packageVmName = packageFQName.replace('.', '/');

    final List<VirtualFile> children = new ArrayList<VirtualFile>();
    final VirtualFile vDir = packageVmName.length() > 0 ? outputpath.findFileByRelativePath(packageVmName) : outputpath;
    if (vDir != null) {
      Collections.addAll(children, vDir.getChildren());
    }

    if (suite.isTrackTestFolders()) {
      final VirtualFile testDir = packageVmName.length() > 0 ? testOutputpath.findFileByRelativePath(packageVmName) : testOutputpath;
      if (testDir != null) {
        Collections.addAll(children, testDir.getChildren());
      }
    }

    final Set<VirtualFile> classFiles = new HashSet<VirtualFile>();
    for (PsiClass psiClass : ((PsiClassOwner)myFile).getClasses()) {
      final String className = psiClass.getName();
      for (VirtualFile child : children) {
        if (child.getFileType().equals(StdFileTypes.CLASS)) {
          final String childName = child.getNameWithoutExtension();
          if (childName.equals(className) ||  //class or inner
              childName.startsWith(className) && childName.charAt(className.length()) == '$') {
            classFiles.add(child);
          }
        }
      }
    }

    final boolean subCoverageActive = CoverageDataManager.getInstance(myProject).isSubCoverageActive();
    final boolean coverageByTestApplicable = suite.isCoverageByTestApplicable() && !(subCoverageActive && suite.isCoverageByTestEnabled());
    final TreeMap<Integer, LineData> executableLines = new TreeMap<Integer, LineData>();
    final TreeMap<Integer, ClassData> classLines = new TreeMap<Integer, ClassData>();
    for (VirtualFile classFile : classFiles) {
      final String qualifiedName = StringUtil.getQualifiedName(packageFQName, classFile.getNameWithoutExtension());
      final ClassData classData = data.getClassData(qualifiedName);
      if (classData != null) {
        final Object[] lines = classData.getLines();
        for (Object lineData : lines) {
          if (lineData instanceof LineData) {
            final int line = ((LineData)lineData).getLineNumber() - 1;
            if (oldToNewLineMapping.contains(line)) {
              final int lineNumberInCurrent = oldToNewLineMapping.get(line);
              LOG.assertTrue(lineNumberInCurrent < myDocument.getLineCount());
              executableLines.put(line, (LineData)lineData);
              classLines.put(line, classData);
              final RangeHighlighter highlighter =
                  createRangeHighlighter(suite.getLastCoverageTimeStamp(), markupModel, coverageByTestApplicable, executableLines,
                                         classData, line, lineNumberInCurrent, suite);
              highlighters.add(highlighter);
            }
          }
        }
      }
      else if (!subCoverageActive && (((CoverageSuiteImpl)suite).isClassFiltered(qualifiedName) || ((CoverageSuiteImpl)suite).isPackageFiltered(packageFQName))) {
        collectNonCoveredClassInfo(classFile, highlighters, markupModel, executableLines, coverageByTestApplicable);
      }
    }

    final DocumentListener documentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        myNewToOldLines = null;
        myOldToNewLines = null;
        final TIntIntHashMap newToOldLineMapping = getNewToOldLineMapping(suite.getLastCoverageTimeStamp());
        if (newToOldLineMapping != null) {
          List<RangeHighlighter> rangeHighlighters = myFile.getUserData(COVERAGE_HIGHLIGHTERS);
          if (rangeHighlighters == null) rangeHighlighters = new ArrayList<RangeHighlighter>();
          int offset = e.getOffset();
          final int lineNumber = myDocument.getLineNumber(offset);
          final int lastLineNumber = myDocument.getLineNumber(offset + e.getNewLength());
          final TextRange changeRange =
              new TextRange(myDocument.getLineStartOffset(lineNumber), myDocument.getLineEndOffset(lastLineNumber));
          for (Iterator<RangeHighlighter> it = rangeHighlighters.iterator(); it.hasNext();) {
            final RangeHighlighter highlighter = it.next();
            if (!highlighter.isValid() || new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset()).intersects(changeRange)) {
              myDocument.getMarkupModel(myProject).removeHighlighter(highlighter);
              it.remove();
            }
          }

          for (int line = lineNumber; line <= lastLineNumber; line++) {
            final int oldLineNumber = newToOldLineMapping.get(line);
            final LineData lineData = executableLines.get(oldLineNumber);
            if (lineData != null) {
              rangeHighlighters.add(
                  createRangeHighlighter(suite.getLastCoverageTimeStamp(), markupModel, coverageByTestApplicable, executableLines,
                                         classLines.get(oldLineNumber), oldLineNumber, line, suite));
            }
          }
          myFile.putUserData(COVERAGE_HIGHLIGHTERS, rangeHighlighters.size() > 0 ? rangeHighlighters : null);
        }
      }
    };
    myDocument.addDocumentListener(documentListener);
    myFile.putUserData(COVERAGE_DOCUMENT_LISTENER, documentListener);

    if (highlighters.size() > 0) {
      myFile.putUserData(COVERAGE_HIGHLIGHTERS, highlighters);
    }
  }

  private RangeHighlighter createRangeHighlighter(final long date, final MarkupModel markupModel,
                                                  final boolean coverageByTestApplicable,
                                                  final TreeMap<Integer, LineData> executableLines, final ClassData classData,
                                                  final int line,
                                                  final int lineNumberInCurrent,
                                                  @NotNull final CoverageSuite coverageSuite) {
    final RangeHighlighter highlighter = markupModel.addLineHighlighter(lineNumberInCurrent, HighlighterLayer.SELECTION - 1, null);
    final CoverageLineMarkerRenderer markerRenderer = CoverageLineMarkerRenderer
        .getRenderer(line, classData, executableLines, coverageByTestApplicable, coverageSuite,
                     new Function<Integer, Integer>() {
          public Integer fun(final Integer newLine) {
            final TIntIntHashMap oldLineMapping = getNewToOldLineMapping(date);
            return oldLineMapping != null ? oldLineMapping.get(newLine.intValue()) : 0;
          }
        },
                     new Function<Integer, Integer>() {
          public Integer fun(final Integer newLine) {
            final TIntIntHashMap newLineMapping = getOldToNewLineMapping(date);
            return newLineMapping != null ? newLineMapping.get(newLine.intValue()) : 0;
          }
        });
    highlighter.setLineMarkerRenderer(markerRenderer);

    final LineData lineData = classData != null ? classData.getLineData(line + 1) : null;
    if (lineData != null && lineData.getStatus() == LineCoverage.NONE) {
      highlighter.setErrorStripeMarkColor(markerRenderer.getErrorStripeColor(myEditor));
      highlighter.setThinErrorStripeMark(true);
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
    return highlighter;
  }

  private void showEditorWarningMessage(final String message) {
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    final VirtualFile vFile = myFile.getVirtualFile();
    assert vFile != null;

    EditorNotificationPanel panel = new EditorNotificationPanel() {
      {
        myLabel.setIcon(ourIcon);
        myLabel.setText(message);
      }
    };

    myFile.putUserData(NOTIFICATION_PANEL, panel);
    final FileEditor[] editors = fileEditorManager.getEditors(vFile);
    for (final FileEditor editor : editors) {
      fileEditorManager.addTopComponent(editor, panel);
    }
  }

  private void collectNonCoveredClassInfo(final VirtualFile classFile,
                                          final List<RangeHighlighter> highlighters,
                                          final MarkupModel markupModel,
                                          final TreeMap<Integer, LineData> executableLines,
                                          final boolean coverageByTestApplicable) {
    final TIntIntHashMap mapping;
    if (classFile.getTimeStamp() < getVirtualFile().getTimeStamp()) {
      mapping = getOldToNewLineMapping(classFile.getTimeStamp());
      if (mapping == null) return;
    }
    else {
      mapping = null;
    }


    final CoverageSuite coverageSuite = CoverageDataManager.getInstance(myProject).getCurrentSuite();

    final List<Integer> uncoveredLines = coverageSuite.collectNotCoveredFileLines(classFile, coverageSuite);
    if (uncoveredLines == null) {
      return;
    }
    final int lineCount = myDocument.getLineCount();
    for (int lineNumber : uncoveredLines) {
      if (lineNumber >= lineCount) {
        continue;
      }

      final int updatedlineNumber = mapping != null ? mapping.get(lineNumber) : lineNumber;

      executableLines.put(updatedlineNumber, null);
      final RangeHighlighter highlighter =
        createRangeHighlighter(classFile.getTimeStamp(), markupModel, coverageByTestApplicable, executableLines, null, lineNumber,
                               updatedlineNumber, coverageSuite);
      highlighters.add(highlighter);
    }
  }

  private VirtualFile getVirtualFile() {
    final VirtualFile vFile = myFile.getVirtualFile();
    LOG.assertTrue(vFile != null);
    return vFile;
  }


  private void coverageDataNotFound(final CoverageSuite suite) {
    showEditorWarningMessage(CodeInsightBundle.message("coverage.data.not.found"));
    CoverageDataManager.getInstance(myProject).removeCoverageSuite(suite);
  }

  public void dispose() {
    hideCoverageData();
  }
}
