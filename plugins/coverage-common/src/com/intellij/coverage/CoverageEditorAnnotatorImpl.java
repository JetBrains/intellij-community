// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineCoverage;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.Alarm;
import com.intellij.util.Function;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class CoverageEditorAnnotatorImpl implements CoverageEditorAnnotator, Disposable {
  private static final Logger LOG = Logger.getInstance(CoverageEditorAnnotatorImpl.class);
  public static final Key<List<RangeHighlighter>> COVERAGE_HIGHLIGHTERS = Key.create("COVERAGE_HIGHLIGHTERS");
  private static final Key<DocumentListener> COVERAGE_DOCUMENT_LISTENER = Key.create("COVERAGE_DOCUMENT_LISTENER");
  public static final Key<Map<FileEditor, EditorNotificationPanel>> NOTIFICATION_PANELS = Key.create("NOTIFICATION_PANELS");

  private PsiFile myFile;
  private Editor myEditor;
  private Document myDocument;
  private final Project myProject;
  private volatile LineHistoryMapper myMapper;

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  public CoverageEditorAnnotatorImpl(final PsiFile file, final Editor editor) {
    myFile = file;
    myEditor = editor;
    myProject = file.getProject();
    myDocument = myEditor.getDocument();
  }

  @Override
  public final void hideCoverage() {
    Editor editor = myEditor;
    PsiFile file = myFile;
    Document document = myDocument;
    if (editor == null || editor.isDisposed() || file == null || document == null) return;
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    removeHighlighters();

    final Map<FileEditor, EditorNotificationPanel> map = file.getCopyableUserData(NOTIFICATION_PANELS);
    if (map != null) {
      final VirtualFile vFile = getVirtualFile(file);
      boolean freeAll = !fileEditorManager.isFileOpen(vFile);
      file.putCopyableUserData(NOTIFICATION_PANELS, null);
      for (FileEditor fileEditor : map.keySet()) {
        if (!freeAll && !isCurrentEditor(fileEditor)) {
          continue;
        }
        fileEditorManager.removeTopComponent(fileEditor, map.get(fileEditor));
      }
    }

    final DocumentListener documentListener = editor.getUserData(COVERAGE_DOCUMENT_LISTENER);
    if (documentListener != null) {
      document.removeDocumentListener(documentListener);
      editor.putUserData(COVERAGE_DOCUMENT_LISTENER, null);
    }
  }

  private synchronized void removeHighlighters() {
    var editor = myEditor;
    if (editor == null || editor.isDisposed()) return;
    var highlighters = editor.getUserData(COVERAGE_HIGHLIGHTERS);
    if (highlighters != null) {
      for (var highlighter : highlighters) {
        ApplicationManager.getApplication().invokeLater(() -> highlighter.dispose());
      }
      editor.putUserData(COVERAGE_HIGHLIGHTERS, null);
    }
  }

  @Nullable
  private synchronized List<RangeHighlighter> getOrCreateHighlighters(boolean init) {
    var editor = myEditor;
    if (editor == null || editor.isDisposed()) return null;
    var highlighters = editor.getUserData(COVERAGE_HIGHLIGHTERS);
    if (highlighters == null && init) {
      highlighters = new ArrayList<>();
      editor.putUserData(COVERAGE_HIGHLIGHTERS, highlighters);
    }
    return highlighters;
  }

  private synchronized void registerOrDisposeHighlighter(RangeHighlighter highlighter) {
    var highlighters = getOrCreateHighlighters(false);
    if (highlighters == null) {
      ApplicationManager.getApplication().invokeLater(() -> highlighter.dispose());
    }
    else {
      highlighters.add(highlighter);
    }
  }

  @Override
  public final void showCoverage(final CoverageSuitesBundle suite) {
    // Store the values of myFile and myEditor in local variables to avoid an NPE after dispose() has been called in the EDT.
    final PsiFile psiFile = myFile;
    final Editor editor = myEditor;
    final Document document = myDocument;
    if (editor == null || psiFile == null || document == null) return;
    final VirtualFile file = getVirtualFile(psiFile);
    if (file == null || !file.isValid()) return;

    if (getOrCreateHighlighters(false) != null) {
      //highlighters already collected - no need to do it twice
      return;
    }

    final CoverageEngine engine = suite.getCoverageEngine();
    final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(psiFile));
    if (module != null) {
      if (engine.recompileProjectAndRerunAction(module, suite, () -> CoverageDataManager.getInstance(myProject).chooseSuitesBundle(suite))) {
        return;
      }
    }

    // let's find old content in local history and build mapping from old lines to new one
    final long fileTimeStamp = file.getTimeStamp();
    final long coverageTimeStamp = suite.getLastCoverageTimeStamp();
    synchronized (this) {
      if (myMapper == null || myMapper.getTimeStamp() != coverageTimeStamp) {
        myMapper = new LineHistoryMapper(myProject, file, document, coverageTimeStamp);
      }
    }
    final Int2IntMap oldToNewLineMapping;
    // local history doesn't index libraries, so let's distinguish libraries content with other one
    if (engine.isInLibrarySource(myProject, file)) {
      if (fileTimeStamp > coverageTimeStamp) {
        showEditorWarningMessage(CoverageBundle.message("coverage.data.outdated"));
        return;
      }
      oldToNewLineMapping = null;
    }
    else if (myMapper.canGetFastMapping()) {
      oldToNewLineMapping = myMapper.getOldToNewLineMapping();
      if (oldToNewLineMapping == null) {
        // if history for file isn't available let's check timestamps
        if (fileTimeStamp > coverageTimeStamp && classesArePresentInCoverageData(suite, engine.getQualifiedNames(psiFile))) {
          showEditorWarningMessage(CoverageBundle.message("coverage.data.outdated"));
          return;
        }
      }
    }
    else {
      oldToNewLineMapping = null;
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var mapping = myMapper.getOldToNewLineMapping();
        if (mapping == null) return;
        removeHighlighters();
        myUpdateAlarm.cancelAllRequests();
        showCoverage(suite);
      });
    }

    // now if oldToNewLineMapping is null we should use f(x) = x mapping
    final MarkupModel markupModel = DocumentMarkupModel.forDocument(document, myProject, true);
    if (getOrCreateHighlighters(true) == null) return;
    final TreeMap<Integer, LineData> executableLines = new TreeMap<>();
    final TreeMap<Integer, String> classNames = new TreeMap<>();
    collectLinesInFile(suite, psiFile, module, oldToNewLineMapping, markupModel, executableLines, classNames);

    if (editor.getUserData(COVERAGE_DOCUMENT_LISTENER) == null) {
      final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void documentChanged(@NotNull final DocumentEvent e) {
          myMapper.clear();
          int offset = e.getOffset();
          final int lineNumber = document.getLineNumber(offset);
          final int lastLineNumber = document.getLineNumber(offset + e.getNewLength());
          if (!removeChangedHighlighters(lineNumber, lastLineNumber, document)) return;
          if (!myUpdateAlarm.isDisposed()) {
            myUpdateAlarm.addRequest(() -> {
              Int2IntMap newToOldLineMapping = myMapper.canGetFastMapping() ? myMapper.getNewToOldLineMapping() : null;
              if (newToOldLineMapping != null) {
                final int lastLine = Math.min(document.getLineCount() - 1, lastLineNumber);
                for (int line = lineNumber; line <= lastLine; line++) {
                  if (!newToOldLineMapping.containsKey(line)) continue;
                  int oldLineNumber = newToOldLineMapping.get(line);
                  var lineData = executableLines.get(oldLineNumber);
                  if (lineData == null) continue;
                  addHighlighter(markupModel, executableLines, suite, oldLineNumber, line, classNames.get(oldLineNumber));
                }
              }
            }, 100);
          }
        }
      };
      document.addDocumentListener(documentListener);
      editor.putUserData(COVERAGE_DOCUMENT_LISTENER, documentListener);
    }
  }

  private synchronized boolean removeChangedHighlighters(int lineNumber, int lastLineNumber, Document document) {
    var rangeHighlighters = getOrCreateHighlighters(false);
    if (rangeHighlighters == null) return false;
    var changeRange = new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lastLineNumber));
    for (var it = rangeHighlighters.iterator(); it.hasNext(); ) {
      final RangeHighlighter highlighter = it.next();
      if (!highlighter.isValid() || highlighter.getTextRange().intersects(changeRange)) {
        ApplicationManager.getApplication().invokeLater(() -> highlighter.dispose());
        it.remove();
      }
    }
    return true;
  }

  protected void collectLinesInFile(@NotNull CoverageSuitesBundle suite,
                                    @NotNull PsiFile psiFile,
                                    Module module,
                                    Int2IntMap oldToNewLineMapping,
                                    @NotNull MarkupModel markupModel,
                                    @NotNull TreeMap<Integer, LineData> executableLines,
                                    @NotNull TreeMap<Integer, String> classNames) {
    var editor = myEditor;
    var engine = suite.getCoverageEngine();
    var data = suite.getCoverageData();
    if (data == null) {
      coverageDataNotFound(suite);
      return;
    }
    class HighlightersCollector {
      private void collect(File outputFile, final String qualifiedName) {
        final ClassData fileData = data.getClassData(qualifiedName);
        if (fileData != null) {
          final Object[] lines = fileData.getLines();
          if (lines != null) {
            final Object[] postProcessedLines = engine.postProcessExecutableLines(lines, editor);
            for (Object o : postProcessedLines) {
              if (o instanceof LineData lineData) {
                if (engine.isGeneratedCode(myProject, qualifiedName, lineData)) continue;
                final int line = (lineData).getLineNumber() - 1;
                final int lineNumberInCurrent;
                if (oldToNewLineMapping != null) {
                  if (!oldToNewLineMapping.containsKey(line)) continue;
                  lineNumberInCurrent = oldToNewLineMapping.get(line);
                }
                else {
                  // use id mapping
                  lineNumberInCurrent = line;
                }
                executableLines.put(line, lineData);
                classNames.put(line, qualifiedName);

                addHighlighter(markupModel, executableLines, suite, line, lineNumberInCurrent, qualifiedName);
              }
            }
          }
        }
        else if (outputFile != null && !CoverageDataManager.getInstance(myProject).isSubCoverageActive() &&
                 engine.includeUntouchedFileInCoverage(qualifiedName, outputFile, psiFile, suite)) {
          highlightNonCoveredFile(outputFile, markupModel, executableLines, suite, oldToNewLineMapping);
        }
      }
    }

    final HighlightersCollector collector = new HighlightersCollector();
    final Set<File> outputFiles = engine.getCorrespondingOutputFiles(psiFile, module, suite);
    if (!outputFiles.isEmpty()) {
      for (File outputFile : outputFiles) {
        final String qualifiedName = engine.getQualifiedName(outputFile, psiFile);
        if (qualifiedName != null) {
          collector.collect(outputFile, qualifiedName);
        }
      }
    }
    else {
      //check non-compilable classes which present in ProjectData
      for (String qName : engine.getQualifiedNames(psiFile)) {
        collector.collect(null, qName);
      }
    }
  }

  private boolean isCoverageByTestApplicable(CoverageSuitesBundle suite) {
    var subCoverageActive = CoverageDataManager.getInstance(myProject).isSubCoverageActive();
    return suite.isCoverageByTestApplicable() && !(subCoverageActive && suite.isCoverageByTestEnabled());
  }

  private static boolean classesArePresentInCoverageData(CoverageSuitesBundle suite, Set<String> qualifiedNames) {
    var data = suite.getCoverageData();
    if (data == null) return false;
    for (String qualifiedName : qualifiedNames) {
      if (data.getClassData(qualifiedName) != null) {
        return true;
      }
    }
    return false;
  }


  /**
   * Additional highlighter in case user redefines coverage color scheme in edtior.
   * It is separated from gutter highlighter as it should have lower layer to be compatible with other inspections.
   */
  @Nullable
  private static RangeHighlighter createBackgroundHighlighter(MarkupModel markupModel,
                                                              @NotNull TreeMap<Integer, LineData> executableLines,
                                                              int line, int lineNumberInCurrent) {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributesKey attributesKey = CoverageLineMarkerRenderer.getAttributesKey(line, executableLines);
    TextAttributes attributes = scheme.getAttributes(attributesKey);
    if (attributes.getBackgroundColor() != null) {
      return markupModel.addLineHighlighter(lineNumberInCurrent, HighlighterLayer.ADDITIONAL_SYNTAX - 1, attributes);
    }
    return null;
  }

  private RangeHighlighter createRangeHighlighter(final MarkupModel markupModel,
                                                  @NotNull final TreeMap<Integer, LineData> executableLines,
                                                  @Nullable final String className,
                                                  final int line,
                                                  final int lineNumberInCurrent,
                                                  @NotNull final CoverageSuitesBundle coverageSuite) {
    // Use maximum layer here for coverage markers to be visible in diff view
    var highlighter = markupModel.addLineHighlighter(lineNumberInCurrent, HighlighterLayer.SELECTION - 1, null);
    Function<Integer, Integer> newToOldConverter = newLine -> {
      var oldLineMapping = myMapper.canGetFastMapping() ? myMapper.getNewToOldLineMapping() : null;
      return oldLineMapping != null ? oldLineMapping.getOrDefault(newLine.intValue(), -1) : newLine;
    };
    Function<Integer, Integer> oldToNewConverter = newLine -> {
      var newLineMapping = myMapper.canGetFastMapping() ? myMapper.getOldToNewLineMapping() : null;
      return newLineMapping != null ? newLineMapping.getOrDefault(newLine.intValue(), -1) : newLine;
    };
    var markerRenderer = coverageSuite.getLineMarkerRenderer(line, className, executableLines, isCoverageByTestApplicable(coverageSuite),
                                                             coverageSuite, newToOldConverter, oldToNewConverter,
                                                             CoverageDataManager.getInstance(myProject).isSubCoverageActive());
    highlighter.setLineMarkerRenderer(markerRenderer);

    final LineData lineData = className != null ? executableLines.get(line) : null;
    var editor = myEditor;
    if (lineData != null && lineData.getStatus() == LineCoverage.NONE && editor != null) {
      highlighter.setErrorStripeMarkColor(markerRenderer.getErrorStripeColor(editor));
      highlighter.setThinErrorStripeMark(true);
      highlighter.setGreedyToLeft(true);
      highlighter.setGreedyToRight(true);
    }
    return highlighter;
  }

  private void showEditorWarningMessage(final @Nls String message) {
    Editor textEditor = myEditor;
    PsiFile file = myFile;
    ApplicationManager.getApplication().invokeLater(() -> {
      if (textEditor == null || textEditor.isDisposed() || file == null) return;
      final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
      final VirtualFile vFile = file.getVirtualFile();
      assert vFile != null;
      Map<FileEditor, EditorNotificationPanel> map = file.getCopyableUserData(NOTIFICATION_PANELS);
      if (map == null) {
        map = new HashMap<>();
        file.putCopyableUserData(NOTIFICATION_PANELS, map);
      }

      final FileEditor[] editors = fileEditorManager.getAllEditors(vFile);
      for (final FileEditor editor : editors) {
        if (isCurrentEditor(editor)) {
          final EditorNotificationPanel panel = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning) {
            {
              myLabel.setIcon(AllIcons.General.ExclMark);
              myLabel.setText(message);
            }
          };
          panel.createActionLabel(CoverageBundle.message("link.label.close"), () -> fileEditorManager.removeTopComponent(editor, panel));
          map.put(editor, panel);
          fileEditorManager.addTopComponent(editor, panel);
          break;
        }
      }
    });
  }

  private boolean isCurrentEditor(FileEditor editor) {
    return editor instanceof TextEditor && ((TextEditor)editor).getEditor() == myEditor;
  }

  private void highlightNonCoveredFile(File outputFile,
                                       MarkupModel markupModel,
                                       TreeMap<Integer, LineData> executableLines, // incomplete for this outputFile
                                       @NotNull CoverageSuitesBundle coverageSuite,
                                       Int2IntMap mapping) {
    var document = myDocument;
    if (document == null) return;
    int lineCount = document.getLineCount();
    var uncoveredLines = coverageSuite.getCoverageEngine().collectSrcLinesForUntouchedFile(outputFile, coverageSuite);
    if (uncoveredLines != null) {
      for (int lineNumber : uncoveredLines) {
        if (executableLines.containsKey(lineNumber)) continue;
        executableLines.put(lineNumber, new LineData(lineNumber, "unknown"));
      }
    }
    for (int lineNumber : (uncoveredLines != null ? uncoveredLines : new IntRange(0, lineCount - 1))) {
      int newLine = lineNumber;
      if (mapping != null) {
        if (!mapping.containsKey(lineNumber)) continue;
        newLine = mapping.get(lineNumber);
      }
      addHighlighter(markupModel, executableLines, coverageSuite, lineNumber, newLine, null);
    }
  }

  protected final void addHighlighter(final MarkupModel markupModel,
                                      @NotNull final TreeMap<Integer, LineData> executableLines,
                                      final CoverageSuitesBundle coverageSuite,
                                      final int lineNumber,
                                      final int updatedLineNumber,
                                      @Nullable String className) {
    ApplicationManager.getApplication().invokeLater(() -> {
      var editor = myEditor;
      var document = myDocument;
      if (editor == null || editor.isDisposed() || document == null) return;
      if (updatedLineNumber < 0 || updatedLineNumber >= document.getLineCount()) return;
      if (lineNumber < 0) return;
      var highlighter = createRangeHighlighter(markupModel, executableLines, className, lineNumber, updatedLineNumber, coverageSuite);
      registerOrDisposeHighlighter(highlighter);
      var backgroundHighlighter = createBackgroundHighlighter(markupModel, executableLines, lineNumber, updatedLineNumber);
      if (backgroundHighlighter != null) {
        registerOrDisposeHighlighter(backgroundHighlighter);
      }
    });
  }

  private static VirtualFile getVirtualFile(PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    LOG.assertTrue(vFile != null);
    return vFile;
  }


  private void coverageDataNotFound(final CoverageSuitesBundle suite) {
    showEditorWarningMessage(CoverageBundle.message("coverage.data.not.found"));
    for (CoverageSuite coverageSuite : suite.getSuites()) {
      CoverageDataManager.getInstance(myProject).removeCoverageSuite(coverageSuite);
    }
  }

  @Override
  public void dispose() {
    hideCoverage();
    myEditor = null;
    myDocument = null;
    myFile = null;
  }
}
