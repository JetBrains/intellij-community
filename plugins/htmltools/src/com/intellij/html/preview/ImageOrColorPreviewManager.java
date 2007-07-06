package com.intellij.html.preview;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public class ImageOrColorPreviewManager implements Disposable, EditorMouseMotionListener {
  private MergingUpdateQueue myQueue;
  private Editor myEditor;
  private PsiFile myFile;
  private LightweightHint myHint;

  public ImageOrColorPreviewManager(@NotNull final TextEditor editor) {
    myEditor = editor.getEditor();

    myEditor.addEditorMouseMotionListener(this);

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(document);


    final JComponent component = editor.getEditor().getComponent();
    myQueue = new MergingUpdateQueue("ImageOrColorPreview", 300, component.isShowing(), component);
    Disposer.register(this, new UiNotifyConnector(editor.getComponent(), myQueue));
  }

  public Editor getEditor() {
    return myEditor;
  }

  @Nullable
  private PsiElement getPsiElementAt(@NotNull final Point point) {
    final LogicalPosition position = getLogicalPosition_(point);
    if (myFile != null) {
      return myFile.getViewProvider().findElementAt(myEditor.logicalPositionToOffset(position));
    }

    return null;
  }

  private LogicalPosition getLogicalPosition(final PsiElement element) {
    return myEditor.offsetToLogicalPosition(element.getTextRange().getEndOffset());
  }

  private LogicalPosition getLogicalPosition_(final Point point) {
    return myEditor.xyToLogicalPosition(point);
  }

  private void setCurrentHint(final LightweightHint hint) {
    if (hint != null) {
      myHint = hint;
    }
  }

  private void hideCurrentHintIfAny() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
    }
  }

  public void dispose() {
    if (myEditor != null) {
      myEditor.removeEditorMouseMotionListener(this);
    }

    if (myQueue != null) {
      myQueue.cancelAllUpdates();
      myQueue.hideNotify();
    }

    myQueue = null;
    myEditor = null;
    myFile = null;
    myHint = null;
  }

  public void mouseMoved(EditorMouseEvent e) {
    myQueue.cancelAllUpdates();
    myQueue.queue(new PreviewUpdate(this, e.getMouseEvent().getPoint()));
  }

  public void mouseDragged(EditorMouseEvent e) {
    // nothing
  }

  @Nullable
  private static LightweightHint getHint(@NotNull PsiElement element) {
    JComponent preview = ColorPreviewComponent.getPreviewComponent(element);
    if (preview == null) {
      preview = ImagePreviewComponent.getPreviewComponent(element);
    }

    return preview == null ? null : new LightweightHint(preview);
  }

  private static final class PreviewUpdate extends Update {
    private ImageOrColorPreviewManager myManager;
    private Point myPoint;

    public PreviewUpdate(@NonNls final ImageOrColorPreviewManager manager, @NotNull final Point point) {
      super(manager);

      myManager = manager;
      myPoint = point;
    }

    public void run() {
      final PsiElement element = myManager.getPsiElementAt(myPoint);
      if (element != null && element.isValid()) {
        final LightweightHint hint = ImageOrColorPreviewManager.getHint(element);
        myManager.setCurrentHint(hint);
        if (hint != null) {
          final Editor editor = myManager.getEditor();
          if (editor != null) {
            HintManager.getInstance().showEditorHint(hint, editor, HintManager.getHintPosition(hint, editor,
                                                                                               myManager.getLogicalPosition(element),
                                                                                               HintManager.RIGHT_UNDER), HintManager
              .HIDE_BY_ANY_KEY | HintManager.HIDE_BY_OTHER_HINT | HintManager.HIDE_BY_SCROLLING | HintManager.HIDE_BY_TEXT_CHANGE |
                               HintManager.HIDE_IF_OUT_OF_EDITOR, 0, false);
          }
        }
        else {
          myManager.hideCurrentHintIfAny();
        }
      }
    }

    public boolean canEat(final Update update) {
      return true;
    }
  }
}
