// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;


import com.intellij.ide.dnd.DnDManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseEvent;


final class EditorTransferHandler extends TransferHandler {

  private static final TransferHandler TRANSFER_HANDLER_STUB = new TransferHandler() {
    @Override
    protected Transferable createTransferable(JComponent c) {
      return null;
    }
  };

  private static final JComponent COMPONENT_STUB = new JComponent() {
    @Override
    public TransferHandler getTransferHandler() {
      return TRANSFER_HANDLER_STUB;
    }
  };

  private static final MouseEvent MOUSE_EVENT_STUB = new MouseEvent(
    /* source = */ new Component() {},
    /* id = */ 0,
    /* when = */ 0L,
    /* modifiers = */ 0,
    /* x = */ 0,
    /* y = */ 0,
    /* clickCount = */ 0,
    /* popupTrigger = */ false,
    /* button = */ 0
  );

  @Override
  public boolean importData(@NotNull TransferSupport support) {
    return support.getComponent() instanceof JComponent jComp &&
           EditorImpl.handleDrop(getEditor(jComp), support.getTransferable(), support.getDropAction());
  }

  @Override
  public boolean canImport(@NotNull JComponent comp, DataFlavor @NotNull [] transferFlavors) {
    EditorImpl editor = getEditor(comp);
    EditorDropHandler dropHandler = editor.getDropHandler();
    if (dropHandler != null && dropHandler.canHandleDrop(transferFlavors)) {
      return true;
    }

    // IDEA-152214 Xcode like way to disable/remove breakpoints
    //should be used a better representation class
    if (Registry.is("debugger.click.disable.breakpoints") &&
        ArrayUtil.contains(GutterDraggableObject.flavor, transferFlavors)) {
      return true;
    }

    // IDEA-169993 Breakpoint remove by drag is not available in breakpoints dialog

    if (editor.isViewer()) return false;

    int offset = editor.getCaretModel().getOffset();
    if (editor.getDocument().getRangeGuard(offset, offset) != null) return false;

    return ArrayUtil.contains(DataFlavor.stringFlavor, transferFlavors);
  }

  @Override
  public int getSourceActions(@NotNull JComponent c) {
    return COPY_OR_MOVE;
  }

  @Override
  protected @Nullable Transferable createTransferable(@NotNull JComponent c) {
    EditorImpl editor = getEditor(c);
    String s = editor.getSelectionModel().getSelectedText();
    if (s == null) return null;
    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();
    // IDEA-134214 drag & drop sometimes copies selection
    editor.setDraggedRange(editor.getDocument().createRangeMarker(selectionStart, selectionEnd));
    Transferable transferable = CopyAction.getSelection(editor);
    // IDEA-233073 Dragging text inside a JavaScript multi-line string replaces tabs with "\t"
    return transferable == null ? new StringSelection(s) : transferable;
  }

  @Override
  protected void exportDone(@NotNull JComponent source, @Nullable Transferable data, int action) {
    if (data == null) return;

    Component last = DnDManager.getInstance().getLastDropHandler();

    // IDEA-287376 Dragging a variable into the Watch window moves it / drag'n'drop the selected value to the Evaluate fields - it's deleted from the Editor
    if (last != null) {
      if (!(last instanceof EditorComponentImpl) && !(last instanceof EditorGutterComponentImpl)) {
        return;
      }
      if (last != source && Boolean.TRUE.equals(EditorImpl.DISABLE_REMOVE_ON_DROP.get(getEditor((JComponent)last)))) {
        return;
      }
    }

    EditorImpl editor = getEditor(source);
    if (action == MOVE && !editor.isViewer() && editor.getDraggedRange() != null) {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(() -> removeDraggedOutFragment(editor));
    }

    editor.clearDnDContext();

    // IDEA-316937 Project leak via TransferHandler$SwingDragGestureRecognizer.
    // This is a dirty, makeshift solution.
    // Swing limits us because javax.swing.TransferHandler.recognizer
    // is a private field, and we cannot reset its value to null to avoid the memory leak.
    // To prevent project leaks, we pass a contextless componentStub for it to replace the previous value of the recognizer field.
    if (source != COMPONENT_STUB) {
      exportAsDrag(COMPONENT_STUB, MOUSE_EVENT_STUB, MOVE);
    }
  }

  private static void removeDraggedOutFragment(@NotNull EditorImpl editor) {
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
      return;
    }
    CommandProcessor.getInstance().executeCommand(
      editor.getProject(),
      () -> ApplicationManager.getApplication().runWriteAction(() -> {
        Document doc = editor.getDocument();
        doc.startGuardedBlockChecking();
        try {
          doc.deleteString(editor.getDraggedRange().getStartOffset(), editor.getDraggedRange().getEndOffset());
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
        }
        finally {
          doc.stopGuardedBlockChecking();
        }
      }),
      EditorBundle.message("move.selection.command.name"),
      EditorImpl.DND_COMMAND_GROUP,
      UndoConfirmationPolicy.DEFAULT,
      editor.getDocument()
    );
  }

  private static @NotNull EditorImpl getEditor(@NotNull JComponent comp) {
    // IDEA-324204 Drag&Drop selected text onto gutter cause ClassCastException and duplicated text
    if (comp instanceof EditorComponentImpl editorComponent) {
      return editorComponent.getEditor();
    }
    return ((EditorGutterComponentImpl)comp).getEditor();
  }
}
