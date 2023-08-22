// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.CommandToken;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author gregsh
 */
public final class ShuffleNamesAction extends AnAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    e.getPresentation().setEnabled(editor != null && file != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || file == null) return;
    final Project project = file.getProject();

    if (!ReadonlyStatusHandler.ensureFilesWritable(project, file.getVirtualFile())) return;

    CommandProcessorEx commandProcessor = (CommandProcessorEx)CommandProcessorEx.getInstance();
    CommandToken commandToken =
      commandProcessor.startCommand(project,
                                    e.getPresentation().getText(),
                                    e.getPresentation().getText(),
                                    UndoConfirmationPolicy.DEFAULT);
    try {
      WriteAction.run(() -> shuffleIds(file, editor));
    }
    finally {
      commandProcessor.finishCommand(commandToken, null);
    }
  }

  private static boolean shuffleIds(PsiFile file, Editor editor) {
    final Map<String, String> map = new HashMap<>();
    final StringBuilder sb = new StringBuilder();
    final StringBuilder quote = new StringBuilder();
    final ArrayList<String> split = new ArrayList<>(100);
    file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof LeafPsiElement) {
          String type = ((LeafPsiElement)element).getElementType().toString();
          String text = element.getText();
          if (text.isEmpty()) return;

          for (int i = 0, len = text.length(); i < len / 2; i++) {
            char c = text.charAt(i);
            if (c == text.charAt(len - i - 1) && !Character.isLetter(c)) {
              quote.append(c);
            }
            else {
              break;
            }
          }

          boolean isQuoted = quote.length() > 0;
          boolean isNumber = false;
          if (isQuoted || type.equals("ID") || type.equals("word") || type.contains("IDENT") && !"ts".equals(text) || //NON-NLS
              (isNumber = text.matches("[0-9]+"))) {
            String replacement = map.get(text);
            if (replacement == null) {
              split.addAll(Arrays.asList(
                (isQuoted ? text.substring(quote.length(), text.length() - quote.length()).replace("''", "") : text).split("")));
              if (!isNumber) {
                for (ListIterator<String> it = split.listIterator(); it.hasNext(); ) {
                  String s = it.next();
                  if (s.isEmpty()) {
                    it.remove();
                    continue;
                  }
                  int c = s.charAt(0);
                  int cap = c & 32;
                  c &= ~cap;
                  c = (char)((c >= 'A') && (c <= 'Z') ? ((c - 'A' + 7) % 26 + 'A') : c) | cap;
                  it.set(String.valueOf((char)c));
                }
              }
              Collections.shuffle(split);
              if (isNumber && "0".equals(split.get(0))) {
                split.set(0, "1");
              }
              replacement = StringUtil.join(split, "");
              if (isQuoted) {
                replacement = quote + replacement + quote.reverse();
              }
              map.put(text, replacement);
            }
            text = replacement;
          }
          sb.append(text);
          quote.setLength(0);
          split.clear();
        }
        super.visitElement(element);
      }
    });
    editor.getDocument().setText(sb.toString());
    return true;
  }
}
