// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.progress.CancellationUtil;
import kotlin.Pair;
import kotlin.ranges.IntRange;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.byteCodeViewer.BytecodeLineMappingKt.mapLines;
import static com.intellij.byteCodeViewer.BytecodeViewerUtilKt.*;

final class BytecodeToolWindowPanel extends JPanel implements Disposable {
  public static final String TOOL_WINDOW_ID = "Java Bytecode";

  private static final Logger LOG = Logger.getInstance(BytecodeToolWindowPanel.class);
  private static final Key<String> BYTECODE_WITH_DEBUG_INFO = Key.create("BYTECODE_WITH_DEBUG_INFO");

  private static final String DEFAULT_TEXT = BytecodeViewerBundle.message("open.java.file.to.see.bytecode");

  private final Project project;
  /// The tool window that this panel is displayed in
  private final ToolWindow toolWindow;
  private final Document bytecodeDocument;
  private final Editor bytecodeEditor;
  private final JLabel classNameLabel;
  private final JLabel errorLabel;

  private @Nullable String currentlyDisplayedClassFQN;
  private @Nullable VirtualFile currentlyFocusedSourceFile;

  private @Nullable LoadBytecodeTask existingLoadBytecodeTask;
  private @Nullable UpdateBytecodeStatusTask existingUpdateBytecodeStatusTask;

  BytecodeToolWindowPanel(Project project, ToolWindow toolWindow, Editor initialSourceEditor) {
    super(new BorderLayout());
    this.project = project;
    this.toolWindow = toolWindow;

    bytecodeDocument = EditorFactory.getInstance().createDocument("");
    // TODO: JavaClassFileType doesn't seem right, because its 'isBinary()' method returns true.
    //  The text we display in the editor is actually a human-readable representation of the bytecode (as returned by ASM ClassReader).
    bytecodeEditor = EditorFactory.getInstance().createEditor(bytecodeDocument, project, JavaClassFileType.INSTANCE, true);
    classNameLabel = new JLabel();
    errorLabel = new JLabel();

    currentlyFocusedSourceFile = initialSourceEditor.getVirtualFile();

    setUpContent();
    WriteAction.run(() -> setBytecodeText(null, DEFAULT_TEXT));
    setUpListeners();

    LOG.trace("Scheduled loading bytecode because the initial tool window setup occurred");
    queueLoadBytecodeTask(() -> updateBytecodeSelection(initialSourceEditor));
  }

  private void setUpContent() {
    bytecodeEditor.setBorder(null);
    add(bytecodeEditor.getComponent());

    JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));

    optionPanel.add(classNameLabel);

    errorLabel.setForeground(JBColor.YELLOW);
    optionPanel.add(errorLabel);

    add(optionPanel, BorderLayout.NORTH);
  }

  private void setUpListeners() {
    MessageBus messageBus = project.getMessageBus();
    MessageBusConnection messageBusConnection = messageBus.connect(this);
    messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (!toolWindow.isVisible()) return;

        currentlyFocusedSourceFile = event.getNewFile();

        final VirtualFile newFile = event.getNewFile();
        if (newFile == null) return;
        if (!isValidFileType(newFile.getFileType())) return;

        final FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(newFile);
        if (!(fileEditor instanceof TextEditor textEditor)) return;
        final Editor sourceEditor = textEditor.getEditor();

        queueLoadBytecodeTask(() -> updateBytecodeSelection(sourceEditor));
        LOG.trace("Scheduled loading bytecode because listener fired: FileEditorManagerListener.selectionChanged()");
      }
    });

    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

    multicaster.addCaretListener(new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        if (!toolWindow.isVisible()) return;

        LOG.trace("Will update bytecode selection because listener fired: CaretListener.caretPositionChanged()");
        updateBytecodeSelection(event.getEditor());
      }
    }, this);
  }

  /// Update only text selection ranges. Do not read bytecode again.
  ///
  /// @param sourceEditor an editor that displays Java code (either real source Java or decompiled Java). If not, this method does nothing.
  @RequiresEdt
  private void updateBytecodeSelection(Editor sourceEditor) {
    if (sourceEditor.getCaretModel().getCaretCount() != 1) return;

    final VirtualFile virtualFile = sourceEditor.getVirtualFile();
    if (virtualFile == null) return;
    if (virtualFile.getFileType() != JavaFileType.INSTANCE) {
      // Only update ranges when viewing the source code.
      LOG.trace("updateBytecodeSelection: file displayed in editor is not Java source, but " + virtualFile.getFileType().getName());
      return;
    }

    final PsiElement selectedPsiElement = getPsiElement(project, sourceEditor);
    if (selectedPsiElement == null) {
      LOG.trace("Tried to update displayed bytecode but the selectedPsiElement is null");
      return;
    }
    final PsiClass containingClass = BytecodeViewerManager.getContainingClass(selectedPsiElement);
    if (containingClass == null) {
      LOG.trace("Tried to update displayed bytecode but the selectedPsiElement (" + selectedPsiElement + ") has no containing class");
      bytecodeEditor.getSelectionModel().removeSelection();
      return;
    }
    if (!Objects.equals(containingClass.getQualifiedName(), currentlyDisplayedClassFQN)) {
      // This is required to correctly handle different classes being present in a single Java file
      LOG.trace("Scheduled loading bytecode because the cursor is now located inside class " +
                containingClass.getQualifiedName() +
                ", which is different from previously displayed class " +
                currentlyDisplayedClassFQN);
      queueLoadBytecodeTask(null);
      return;
    }

    currentlyDisplayedClassFQN = containingClass.getQualifiedName();
    queueUpdateBytecodeStatusTask();

    final int sourceStartOffset = sourceEditor.getCaretModel().getCurrentCaret().getSelectionStart();
    final int sourceEndOffset = sourceEditor.getCaretModel().getCurrentCaret().getSelectionEnd();
    final Document sourceDocument = sourceEditor.getDocument();

    final int sourceStartLine = sourceDocument.getLineNumber(sourceStartOffset);
    int sourceEndLine = sourceDocument.getLineNumber(sourceEndOffset);
    if (sourceEndLine > sourceStartLine && sourceEndOffset > 0 && sourceDocument.getCharsSequence().charAt(sourceEndOffset - 1) == '\n') {
      sourceEndLine--;
    }

    final String bytecodeWithDebugInfo = bytecodeDocument.getUserData(BYTECODE_WITH_DEBUG_INFO);
    if (bytecodeWithDebugInfo == null) {
      LOG.warn("Bytecode with debug information is null. Ensure the bytecode has been generated correctly.");
      return;
    }

    final var linesRange = mapLines(bytecodeWithDebugInfo, sourceStartLine, sourceEndLine, true);

    if (linesRange.equals(new IntRange(0, 0)) || linesRange.getFirst() < 0 || linesRange.getLast() < 0) {
      bytecodeEditor.getSelectionModel().removeSelection();
      return;
    }

    final int endSelectionLineIndex = Math.min(linesRange.getLast() + 1, bytecodeDocument.getLineCount());

    final int startOffset = bytecodeDocument.getLineStartOffset(linesRange.getFirst());
    final int endOffset = Math.min(bytecodeDocument.getLineEndOffset(endSelectionLineIndex), bytecodeDocument.getTextLength());

    if (bytecodeDocument.getTextLength() <= startOffset || bytecodeDocument.getTextLength() <= endOffset) {
      return;
    }

    bytecodeEditor.getCaretModel().moveToOffset(endOffset);
    bytecodeEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    bytecodeEditor.getCaretModel().moveToOffset(startOffset);
    bytecodeEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

    bytecodeEditor.getSelectionModel().setSelection(startOffset, endOffset);

    LOG.trace("updated bytecode selection to lines: (" + linesRange.getFirst() + 1 + ", " + endSelectionLineIndex + 1 + ")");
  }

  private void queueUpdateBytecodeStatusTask() {
    if (currentlyFocusedSourceFile == null) return;

    // If a new task was scheduled, we want to cancel the previous one.
    if (existingUpdateBytecodeStatusTask != null) {
      if (existingUpdateBytecodeStatusTask.isRunning()) {
        existingUpdateBytecodeStatusTask.cancel();
      }
      existingUpdateBytecodeStatusTask = null;
    }
    existingUpdateBytecodeStatusTask = new UpdateBytecodeStatusTask(project, currentlyFocusedSourceFile, (Boolean isUpToDate) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!isUpToDate) {
          setErrorText(BytecodeViewerBundle.message("class.file.may.be.out.of.date"));
        }
        else {
          setErrorText(null);
        }
      });
    });
    existingUpdateBytecodeStatusTask.queue();
  }

  /// Update the contents of the whole editor in the tool window, including reading bytecode again from the currently opened file.
  @RequiresEdt
  private void queueLoadBytecodeTask(@RequiresWriteLock @RequiresEdt @Nullable Runnable onAfterBytecodeLoaded) {
    final Consumer<PsiClass> onClassUpdated = (PsiClass newClass) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        currentlyDisplayedClassFQN = newClass.getQualifiedName();
        setClassName(newClass.getName());
      });
    };

    final Consumer<Bytecode> onNewBytecodeLoaded = (Bytecode bytecode) -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        WriteAction.run(() -> {
          setBytecodeText(bytecode.withDebugInfo(), bytecode.withoutDebugInfo());
          if (onAfterBytecodeLoaded != null) {
            onAfterBytecodeLoaded.run();
          }
        });
      });
    };

    // If a new task was scheduled to update bytecode, we want to cancel the previous one.
    if (existingLoadBytecodeTask != null) {
      if (existingLoadBytecodeTask.isRunning()) {
        LOG.trace("queueLoadBytecodeTask(): canceling existing LoadBytecodeTask " + existingLoadBytecodeTask.hashCode());
        existingLoadBytecodeTask.cancel();
      }
      existingLoadBytecodeTask = null;
    }
    existingLoadBytecodeTask = new LoadBytecodeTask(project, onNewBytecodeLoaded, onClassUpdated);
    existingLoadBytecodeTask.queue();
  }

  @RequiresEdt
  @RequiresWriteLock
  private void setBytecodeText(@Nullable String bytecodeWithDebugInfo, @NotNull String bytecodeWithoutDebugInfo) {
    bytecodeEditor.getDocument().putUserData(BYTECODE_WITH_DEBUG_INFO, bytecodeWithDebugInfo);
    bytecodeEditor.getDocument().setText(StringUtil.convertLineSeparators(bytecodeWithoutDebugInfo));
  }

  @RequiresEdt
  private void setErrorText(@Nls @Nullable String errorText) {
    errorLabel.setText(errorText);
    errorLabel.setVisible(errorText != null);
  }

  @RequiresEdt
  private void setClassName(@NlsSafe String className) {
    classNameLabel.setText(BytecodeViewerBundle.message("bytecode.for.class", className));
    classNameLabel.setVisible(className != null);
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(bytecodeEditor);
  }
}


final class LoadBytecodeTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance(LoadBytecodeTask.class);

  private final @NotNull Consumer<@NotNull Bytecode> onBytecodeUpdated;
  private final @NotNull Consumer<@NotNull PsiClass> onClassNameUpdated;

  private @Nullable ProgressIndicator myProgressIndicator;
  private @Nullable Bytecode myBytecode;

  LoadBytecodeTask(@NotNull Project project,
                   @RequiresEdt @NotNull Consumer<@NotNull Bytecode> onBytecodeUpdated,
                   @RequiresBackgroundThread @NotNull Consumer<@NotNull PsiClass> onClassUpdated) {
    super(project, BytecodeViewerBundle.message("loading.bytecode"), true);
    this.onBytecodeUpdated = onBytecodeUpdated;
    this.onClassNameUpdated = onClassUpdated;
  }

  public void cancel() {
    LOG.trace("canceled");
    if (myProgressIndicator != null) {
      myProgressIndicator.cancel();
    }
  }

  public boolean isRunning() {
    return myProgressIndicator != null && myProgressIndicator.isRunning();
  }

  @RequiresBackgroundThread
  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    if (myProject == null) return;
    myProgressIndicator = indicator;
    myBytecode = ReadAction.computeCancellable(() -> {
      final Editor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      if (selectedEditor == null) {
        LOG.warn("Tried to show Java bytecode but selectedEditor is null");
        return null;
      }

      final PsiFile psiFileInEditor = PsiUtilBase.getPsiFileInEditor(selectedEditor, myProject);
      if (psiFileInEditor == null) {
        LOG.warn("Tried to update displayed bytecode but psiFileInEditor is null");
        return null;
      }
      if (!isValidFileType(psiFileInEditor.getFileType())) {
        LOG.warn("Tried to update displayed bytecode for invalid file type " + psiFileInEditor.getFileType().getName());
        return null;
      }

      final PsiElement selectedPsiElement = getPsiElement(myProject, selectedEditor);
      if (selectedPsiElement == null) {
        LOG.warn("Tried to update displayed bytecode but the selectedPsiElement is null");
        return null;
      }

      final PsiClass containingClass = BytecodeViewerManager.getContainingClass(selectedPsiElement);
      if (containingClass == null) {
        LOG.trace("Tried to update displayed bytecode but the selectedPsiElement (" + selectedPsiElement + ") has no containing class");
        return null;
      }

      onClassNameUpdated.accept(containingClass);

      //CancellationUtil.sleepCancellable(1000); // Uncomment if you want to make sure we continue to not freeze the IDE

      final Pair<String, String> bytecodeVariants = getByteCodeVariants(selectedPsiElement);
      if (bytecodeVariants == null) {
        LOG.warn("Tried to update displayed bytecode but bytecode is null. selectedPsiElement: " + selectedPsiElement);
        return null;
      }

      return new Bytecode(bytecodeVariants.getFirst(), bytecodeVariants.getSecond());
    });
  }

  @RequiresEdt
  @Override
  public void onSuccess() {
    LOG.trace("onSuccess(): bytecode != null? " + (myBytecode != null));
    if (myBytecode != null) {
      onBytecodeUpdated.accept(myBytecode);
    }
  }

  @RequiresEdt
  @Override
  public void onCancel() {
    if (myProgressIndicator == null) return;
    LOG.warn("task was canceled, task title: " + getTitle() + "task text: " + myProgressIndicator.getText());
  }
}

final class UpdateBytecodeStatusTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance(UpdateBytecodeStatusTask.class);

  private final @NotNull VirtualFile myVirtualFile;
  private final @NotNull Consumer<Boolean> onUpToDateCheckDone;

  private @Nullable ProgressIndicator myProgressIndicator;

  UpdateBytecodeStatusTask(@NotNull Project project, @NotNull VirtualFile virtualFile, @NotNull Consumer<Boolean> onUpToDateCheckDone) {
    super(project, BytecodeViewerBundle.message("checking.if.bytecode.is.up.to.date"), true);
    this.myVirtualFile = virtualFile;
    this.onUpToDateCheckDone = onUpToDateCheckDone;
  }

  public void cancel() {
    if (myProgressIndicator != null) {
      myProgressIndicator.cancel();
    }
  }

  public boolean isRunning() {
    return myProgressIndicator != null && myProgressIndicator.isRunning();
  }

  @RequiresBackgroundThread
  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    if (myProject == null) return;
    LOG.trace("run() started!");
    if (!myVirtualFile.isValid()) {
      LOG.trace("run() canceled because file" + myVirtualFile + " is invalid");
      return;
    }

    myProgressIndicator = indicator;

    CancellationUtil.sleepCancellable(1000); // Poor man's event debouncing

    final boolean isInContent = ReadAction.computeCancellable(() -> {
      return ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(myVirtualFile);
    });
    if (!isInContent) {
      LOG.trace("run() returns early because file " + myVirtualFile + " is not in the project's content");
      return;
    }

    final boolean isUpToDate = !isMarkedForCompilation(myProject, myVirtualFile);
    LOG.trace("up-to-date check for file " + myVirtualFile + " finished, is up to date: " + isUpToDate);
    onUpToDateCheckDone.accept(isUpToDate);
  }
}

record Bytecode(@NotNull String withDebugInfo, @NotNull String withoutDebugInfo) {
}
