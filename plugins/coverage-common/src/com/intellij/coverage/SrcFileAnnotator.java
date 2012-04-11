/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.coverage;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.reference.SoftReference;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.Function;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
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
  public static final Key<Map<FileEditor, EditorNotificationPanel>> NOTIFICATION_PANELS = Key.create("NOTIFICATION_PANELS");

  private final PsiFile myFile;
  private final Editor myEditor;
  private final Document myDocument;
  private final Project myProject;
  private static final Icon ourIcon = IconLoader.getIcon("/general/exclMark.png");

  private SoftReference<TIntIntHashMap> myNewToOldLines;
  private SoftReference<TIntIntHashMap> myOldToNewLines;
  private SoftReference<byte[]> myOldContent;
  private final static Object LOCK = new Object();

  public SrcFileAnnotator(final PsiFile file, final Editor editor) {
    myFile = file;
    myEditor = editor;
    myProject = file.getProject();
    myDocument = myEditor.getDocument();
  }

  public void hideCoverageData() {
    removeHighlights();
  }

  private void removeHighlights() {
    final List<RangeHighlighter> highlighters = myFile.getUserData(COVERAGE_HIGHLIGHTERS);
    if (highlighters != null) {
      for (final RangeHighlighter highlighter : highlighters) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            highlighter.dispose();
          }
        });
      }
      myFile.putUserData(COVERAGE_HIGHLIGHTERS, null);
    }
    else {
      final Map<FileEditor, EditorNotificationPanel> map = myFile.getCopyableUserData(NOTIFICATION_PANELS);
      if (map != null) {
        myFile.putCopyableUserData(NOTIFICATION_PANELS, null);
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        for (FileEditor fileEditor : map.keySet()) {
          fileEditorManager.removeTopComponent(fileEditor, map.get(fileEditor));
        }
      }
    }
    final DocumentListener documentListener = myFile.getUserData(COVERAGE_DOCUMENT_LISTENER);
    if (documentListener != null) {
      myDocument.removeDocumentListener(documentListener);
      myFile.putUserData(COVERAGE_DOCUMENT_LISTENER, null);
    }
  }

  public void editorReleased() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    final VirtualFile vFile = myFile.getVirtualFile();
    LOG.assertTrue(vFile != null);
    if (fileEditorManager.isFileOpen(vFile)) {
      removeWarningMessage(fileEditorManager);
    }
    else {
      removeHighlights();
    }
  }

  private void removeWarningMessage(FileEditorManager fileEditorManager) {
    final Map<FileEditor, EditorNotificationPanel> map = myFile.getCopyableUserData(NOTIFICATION_PANELS);
    if (map != null) {
      for (FileEditor fileEditor : map.keySet()) {
        if (isCurrentEditor(fileEditor)) {
          fileEditorManager.removeTopComponent(fileEditor, map.get(fileEditor));
          map.remove(fileEditor);
          break;
        }
      }
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
    final String[] lines = new String[lineCount];
    final Runnable runnable = new Runnable() {
      public void run() {
        final CharSequence chars = myDocument.getCharsSequence();
        for (int i = 0; i < lineCount; i++) {
          lines[i] = chars.subSequence(myDocument.getLineStartOffset(i), myDocument.getLineEndOffset(i)).toString();
        }
      }
    };
    ApplicationManager.getApplication().runReadAction(runnable);

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
    final VirtualFile f = getVirtualFile();
    final byte[] oldContent;
    synchronized (LOCK) {
      if (myOldContent == null) {
        if (ApplicationManager.getApplication().isDispatchThread()) return null;
        final byte[] byteContent = LocalHistory.getInstance().getByteContent(f, new FileRevisionTimestampComparator() {
          public boolean isSuitable(long revisionTimestamp) {
            return revisionTimestamp < date;
          }
        });
        myOldContent = new SoftReference<byte[]>(byteContent);
      }
      oldContent = myOldContent.get();
    }

    if (oldContent == null) return null;
    String[] coveredLines = getCoveredLines(oldContent, f);
    String[] currentLines = getUpToDateLines();

    String[] oldLines = oldToNew ? coveredLines : currentLines;
    String[] newLines = oldToNew ? currentLines : coveredLines;

    Diff.Change change = null;
    try {
      change = Diff.buildChanges(oldLines, newLines);
    }
    catch (FilesTooBigForDiffException e) {
      LOG.info(e);
      return null;
    }
    return new SoftReference<TIntIntHashMap>(getCoverageVersionToCurrentLineMapping(change, oldLines.length));
  }

  public void showCoverageInformation(final CoverageSuitesBundle suite) {
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(myDocument, myProject, true);
    final List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    final ProjectData data = suite.getCoverageData();
    if (data == null) {
      coverageDataNotFound(suite);
      return;
    }
    final CoverageEngine engine = suite.getCoverageEngine();
    final Set<String> qualifiedNames = engine.getQualifiedNames(myFile);

    // let's find old content in local history and build mapping from old lines to new one
    // local history doesn't index libraries, so let's distinguish libraries content with other one
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile file = getVirtualFile();

    final long fileTimeStamp = file.getTimeStamp();
    final long coverageTimeStamp = suite.getLastCoverageTimeStamp();
    final TIntIntHashMap oldToNewLineMapping;

    //do not show coverage info over cls
    if (projectFileIndex.isInLibraryClasses(file)) {
      return;
    }
    // if in libraries content
    if (projectFileIndex.isInLibrarySource(file)) {
      // compare file and coverage timestamps
      if (fileTimeStamp > coverageTimeStamp) {
        showEditorWarningMessage(CodeInsightBundle.message("coverage.data.outdated"));
        return;
      }
      oldToNewLineMapping = null;
    }
    else {
      // check local history
      oldToNewLineMapping = getOldToNewLineMapping(coverageTimeStamp);
      if (oldToNewLineMapping == null) {

        // if history for file isn't available let's check timestamps
        if (fileTimeStamp > coverageTimeStamp && classesArePresentInCoverageData(data, qualifiedNames)) {
          showEditorWarningMessage(CodeInsightBundle.message("coverage.data.outdated"));
          return;
        }
      }
    }

    if (myFile.getUserData(COVERAGE_HIGHLIGHTERS) != null) {
      //highlighters already collected - no need to do it twice
      return;
    }

    final Module module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
      @Nullable
      @Override
      public Module compute() {
        return ModuleUtil.findModuleForPsiElement(myFile);
      }
    });
    if (module != null) {
      if (engine.recompileProjectAndRerunAction(module, suite, new Runnable() {
        public void run() {
          CoverageDataManager.getInstance(myProject).chooseSuitesBundle(suite);
        }
      })) {
        return;
      }
    }

    // now if oldToNewLineMapping is null we should use f(x)=id(x) mapping

    // E.g. all *.class files for java source file with several classes
    final Set<VirtualFile> outputFiles = engine.getCorrespondingOutputFiles(myFile, module, suite);

    final boolean subCoverageActive = CoverageDataManager.getInstance(myProject).isSubCoverageActive();
    final boolean coverageByTestApplicable = suite.isCoverageByTestApplicable() && !(subCoverageActive && suite.isCoverageByTestEnabled());
    final TreeMap<Integer, LineData> executableLines = new TreeMap<Integer, LineData>();
    final TreeMap<Integer, Object[]> classLines = new TreeMap<Integer, Object[]>();
    final TreeMap<Integer, String> classNames = new TreeMap<Integer, String>();
    class HighlightersCollector {
      private void collect(VirtualFile outputFile, final String qualifiedName) {
        final ClassData fileData = data.getClassData(qualifiedName);
        if (fileData != null) {
          final Object[] lines = fileData.getLines();
          if (lines != null) {
            final Object[] postProcessedLines = suite.getCoverageEngine().postProcessExecutableLines(lines, myEditor);
            for (Object lineData : postProcessedLines) {
              if (lineData instanceof LineData) {
                final int line = ((LineData)lineData).getLineNumber() - 1;
                final int lineNumberInCurrent;
                if (oldToNewLineMapping != null) {
                  // use mapping based on local history
                  if (!oldToNewLineMapping.contains(line)) {
                    continue;
                  }
                  lineNumberInCurrent = oldToNewLineMapping.get(line);
                }
                else {
                  // use id mapping
                  lineNumberInCurrent = line;
                }
                LOG.assertTrue(lineNumberInCurrent < myDocument.getLineCount());
                executableLines.put(line, (LineData)lineData);
  
                classLines.put(line, postProcessedLines);
                classNames.put(line, qualifiedName);
  
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    if (lineNumberInCurrent >= myDocument.getLineCount()) return;
                    final RangeHighlighter highlighter =
                      createRangeHighlighter(suite.getLastCoverageTimeStamp(), markupModel, coverageByTestApplicable, executableLines,
                                             qualifiedName, line, lineNumberInCurrent, suite, postProcessedLines);
                    highlighters.add(highlighter);
                  }
                });
              }
            }
          }
        }
        else if (outputFile != null &&
                 !subCoverageActive &&
                 engine.includeUntouchedFileInCoverage(qualifiedName, outputFile, myFile, suite)) {
          collectNonCoveredFileInfo(outputFile, highlighters, markupModel, executableLines, coverageByTestApplicable);
        }
      }
    }

    final HighlightersCollector collector = new HighlightersCollector();
    if (!outputFiles.isEmpty()) {
      for (VirtualFile outputFile : outputFiles) {
        final String qualifiedName = engine.getQualifiedName(outputFile, myFile);
        if (qualifiedName != null) {
          collector.collect(outputFile, qualifiedName);
        }
      }
    }
    else { //check non-compilable classes which present in ProjectData
      for (String qName : qualifiedNames) {
        collector.collect(null, qName);
      }
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (highlighters.size() > 0) {
          myFile.putUserData(COVERAGE_HIGHLIGHTERS, highlighters);
        }
      }
    });

    final DocumentListener documentListener = new DocumentAdapter() {
      @Override
      public void documentChanged(final DocumentEvent e) {
        myNewToOldLines = null;
        myOldToNewLines = null;
        List<RangeHighlighter> rangeHighlighters = myFile.getUserData(COVERAGE_HIGHLIGHTERS);
        if (rangeHighlighters == null) rangeHighlighters = new ArrayList<RangeHighlighter>();
        int offset = e.getOffset();
        final int lineNumber = myDocument.getLineNumber(offset);
        final int lastLineNumber = myDocument.getLineNumber(offset + e.getNewLength());
        final TextRange changeRange =
          new TextRange(myDocument.getLineStartOffset(lineNumber), myDocument.getLineEndOffset(lastLineNumber));
        for (Iterator<RangeHighlighter> it = rangeHighlighters.iterator(); it.hasNext(); ) {
          final RangeHighlighter highlighter = it.next();
          if (!highlighter.isValid() || TextRange.create(highlighter).intersects(changeRange)) {
            highlighter.dispose();
            it.remove();
          }
        }
        final List<RangeHighlighter> highlighters = rangeHighlighters;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            final TIntIntHashMap newToOldLineMapping = getNewToOldLineMapping(suite.getLastCoverageTimeStamp());
            if (newToOldLineMapping != null) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  for (int line = lineNumber; line <= lastLineNumber; line++) {
                    final int oldLineNumber = newToOldLineMapping.get(line);
                    final LineData lineData = executableLines.get(oldLineNumber);
                    if (lineData != null) {
                      RangeHighlighter rangeHighlighter =
                        createRangeHighlighter(suite.getLastCoverageTimeStamp(), markupModel, coverageByTestApplicable, executableLines,
                                               classNames.get(oldLineNumber), oldLineNumber, line, suite,
                                               classLines.get(oldLineNumber));
                      highlighters.add(rangeHighlighter);
                    }
                  }
                  myFile.putUserData(COVERAGE_HIGHLIGHTERS, highlighters.size() > 0 ? highlighters : null);
                }
              });
            }
          }
        });
      }
    };
    myDocument.addDocumentListener(documentListener);
    myFile.putUserData(COVERAGE_DOCUMENT_LISTENER, documentListener);
  }

  private static boolean classesArePresentInCoverageData(ProjectData data, Set<String> qualifiedNames) {
    for (String qualifiedName : qualifiedNames) {
      if (data.getClassData(qualifiedName) != null) {
        return true;
      }
    }
    return false;
  }

  private RangeHighlighter createRangeHighlighter(final long date, final MarkupModel markupModel,
                                                  final boolean coverageByTestApplicable,
                                                  final TreeMap<Integer, LineData> executableLines, @Nullable final String className,
                                                  final int line,
                                                  final int lineNumberInCurrent,
                                                  @NotNull final CoverageSuitesBundle coverageSuite, Object[] lines) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    final TextAttributes attributes = scheme.getAttributes(CoverageLineMarkerRenderer.getAttributesKey(line, executableLines));
    TextAttributes textAttributes = null;
    if (attributes.getBackgroundColor() != null) {
      textAttributes = attributes;
    }
    final RangeHighlighter highlighter =
      markupModel.addLineHighlighter(lineNumberInCurrent, HighlighterLayer.SELECTION - 1, textAttributes);
    final Function<Integer, Integer> newToOldConverter = new Function<Integer, Integer>() {
      public Integer fun(final Integer newLine) {
        final TIntIntHashMap oldLineMapping = getNewToOldLineMapping(date);
        return oldLineMapping != null ? oldLineMapping.get(newLine.intValue()) : newLine.intValue();
      }
    };
    final Function<Integer, Integer> oldToNewConverter = new Function<Integer, Integer>() {
      public Integer fun(final Integer newLine) {
        final TIntIntHashMap newLineMapping = getOldToNewLineMapping(date);
        return newLineMapping != null ? newLineMapping.get(newLine.intValue()) : newLine.intValue();
      }
    };
    final CoverageLineMarkerRenderer markerRenderer = coverageSuite.getCoverageEngine()
      .getLineMarkerRenderer(line, className, executableLines, coverageByTestApplicable, coverageSuite, newToOldConverter,
                             oldToNewConverter, CoverageDataManager.getInstance(myProject).isSubCoverageActive());
    highlighter.setLineMarkerRenderer(markerRenderer);

    final LineData lineData = className != null ? (LineData)lines[line + 1] : null;
    if (lineData != null && lineData.getStatus() == LineCoverage.NONE) {
      highlighter.setErrorStripeMarkColor(markerRenderer.getErrorStripeColor(myEditor));
      highlighter.setThinErrorStripeMark(true);
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
    return highlighter;
  }

  private void showEditorWarningMessage(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        final VirtualFile vFile = myFile.getVirtualFile();
        assert vFile != null;
        Map<FileEditor, EditorNotificationPanel> map = myFile.getCopyableUserData(NOTIFICATION_PANELS);
        if (map == null) {
          map = new HashMap<FileEditor, EditorNotificationPanel>();
          myFile.putCopyableUserData(NOTIFICATION_PANELS, map);
        }

        final FileEditor[] editors = fileEditorManager.getAllEditors(vFile);
        for (final FileEditor editor : editors) {
          if (isCurrentEditor(editor)) {
            EditorNotificationPanel panel = new EditorNotificationPanel() {
              {
                myLabel.setIcon(ourIcon);
                myLabel.setText(message);
              }
            };
            map.put(editor, panel);
            fileEditorManager.addTopComponent(editor, panel);
            break;
          }
        }
      }
    });
  }

  private boolean isCurrentEditor(FileEditor editor) {
    return editor instanceof TextEditor && ((TextEditor)editor).getEditor() == myEditor;
  }

  private void collectNonCoveredFileInfo(final VirtualFile outputFile,
                                         final List<RangeHighlighter> highlighters, final MarkupModel markupModel,
                                         final TreeMap<Integer, LineData> executableLines,
                                         final boolean coverageByTestApplicable) {
    final CoverageSuitesBundle coverageSuite = CoverageDataManager.getInstance(myProject).getCurrentSuitesBundle();
    if (coverageSuite == null) return;
    final TIntIntHashMap mapping;
    if (outputFile.getTimeStamp() < getVirtualFile().getTimeStamp()) {
      mapping = getOldToNewLineMapping(outputFile.getTimeStamp());
      if (mapping == null) return;
    }
    else {
      mapping = null;
    }


    final List<Integer> uncoveredLines = coverageSuite.getCoverageEngine().collectSrcLinesForUntouchedFile(outputFile, coverageSuite);

    final int lineCount = myDocument.getLineCount();
    if (uncoveredLines == null) {
      for (int lineNumber = 0; lineNumber < lineCount; lineNumber++) {
        addHighlighter(outputFile, highlighters, markupModel, executableLines, coverageByTestApplicable, coverageSuite,
                       lineNumber, lineNumber);
      }
    }
    else {
      for (int lineNumber : uncoveredLines) {
        if (lineNumber >= lineCount) {
          continue;
        }

        final int updatedLineNumber = mapping != null ? mapping.get(lineNumber) : lineNumber;

        addHighlighter(outputFile, highlighters, markupModel, executableLines, coverageByTestApplicable, coverageSuite,
                       lineNumber, updatedLineNumber);
      }
    }
  }

  private void addHighlighter(final VirtualFile outputFile,
                              final List<RangeHighlighter> highlighters,
                              final MarkupModel markupModel,
                              final TreeMap<Integer, LineData> executableLines,
                              final boolean coverageByTestApplicable,
                              final CoverageSuitesBundle coverageSuite,
                              final int lineNumber,
                              final int updatedLineNumber) {
    executableLines.put(updatedLineNumber, null);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final RangeHighlighter highlighter =
          createRangeHighlighter(outputFile.getTimeStamp(), markupModel, coverageByTestApplicable, executableLines, null, lineNumber,
                                 updatedLineNumber, coverageSuite, null);
        highlighters.add(highlighter);
      }
    });
  }

  private VirtualFile getVirtualFile() {
    final VirtualFile vFile = myFile.getVirtualFile();
    LOG.assertTrue(vFile != null);
    return vFile;
  }


  private void coverageDataNotFound(final CoverageSuitesBundle suite) {
    showEditorWarningMessage(CodeInsightBundle.message("coverage.data.not.found"));
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      CoverageDataManager.getInstance(myProject).removeCoverageSuite(coverageSuite);
    }
  }

  public void dispose() {
    hideCoverageData();
  }
}
