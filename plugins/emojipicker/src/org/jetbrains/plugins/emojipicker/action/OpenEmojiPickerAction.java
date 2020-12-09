// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.emojipicker.ui.EmojiPicker;
import org.jetbrains.plugins.emojipicker.ui.EmojiSearchField;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.function.Consumer;

/**
 * An action that opens emoji picker popup, selected by user.
 */
public class OpenEmojiPickerAction extends DumbAwareAction {

  private static Context getContext(AnActionEvent e, boolean findOnly) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor != null) {
      if (editor.isViewer()) return null;
      if (findOnly) return Context.FOUND;
      Consumer<String> input;
      if (editor instanceof EditorImpl) {
        input = ((EditorImpl)editor)::type;
      }
      else if (editor.getContentComponent() instanceof TypingTarget) {
        input = ((TypingTarget)editor.getContentComponent())::type;
      }
      else {
        input = s -> WriteCommandAction.writeCommandAction(editor.getProject()).run(() -> {
          com.intellij.openapi.editor.Document doc = editor.getDocument();
          editor.getCaretModel().runForEachCaret(c -> {
            doc.insertString(c.getOffset(), s);
            c.moveCaretRelatively(1, 0, false, false);
          });
        });
      }
      return new Context(input, p -> p.showInBestPositionFor(editor));
    }

    Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof EmojiSearchField) {
      // Easy way to block recursive pickers
      return null;
    }

    if (component instanceof TypingTarget) {
      if (findOnly) return Context.FOUND;
      return new Context(
        ((TypingTarget)component)::type,
        p -> p.showUnderneathOf(component)
      );
    }

    if (component instanceof JTextComponent) {
      if (findOnly) return Context.FOUND;
      JTextComponent field = (JTextComponent)component;
      Document doc = field.getDocument();
      return new Context(
        s -> {
          try {
            doc.insertString(field.getCaretPosition(), s, null);
          }
          catch (BadLocationException exception) {
            throw new RuntimeException(exception);
          }
        },
        p -> p.showUnderneathOf(component)
      );
    }

    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = SystemInfo.isLinux && EmojiPicker.isAvailable() && getContext(e, true) == Context.FOUND;
    e.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Context context = getContext(e, false);
    if (context != null) {
      JBPopup popup = EmojiPicker.createPopup(e.getProject(), context.myInputCallback);
      context.myPopupShowCallback.accept(popup);
    }
  }


  private static class Context {
    private static final Context FOUND = new Context(null, null);
    private final Consumer<String> myInputCallback;
    private final Consumer<JBPopup> myPopupShowCallback;

    private Context(Consumer<String> inputCallback, Consumer<JBPopup> popupShowCallback) {
      myInputCallback = inputCallback;
      myPopupShowCallback = popupShowCallback;
    }
  }
}
