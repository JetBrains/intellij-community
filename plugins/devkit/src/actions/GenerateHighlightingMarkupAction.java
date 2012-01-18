/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.actions;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * @author gregsh
 */
public class GenerateHighlightingMarkupAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    PsiFile file = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    e.getPresentation().setEnabled(editor != null && file != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
    PsiFile file = LangDataKeys.PSI_FILE.getData(e.getDataContext());
    if (editor == null || file == null) return;
    final Project project = file.getProject();
    CommandProcessorEx commandProcessorEx = (CommandProcessorEx)CommandProcessorEx.getInstance();
    Object commandToken = commandProcessorEx
      .startCommand(project, e.getPresentation().getText(), e.getPresentation().getText(), UndoConfirmationPolicy.DEFAULT);
    AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
    try {
      perform(project, editor.getDocument());
    }
    finally {
      token.finish();
      commandProcessorEx.finishCommand(project, commandToken, null);
    }
  }

  private static void perform(Project project, final Document document) {
    final CharSequence sequence = document.getCharsSequence();
    final StringBuilder sb = new StringBuilder();
    final int[] offset = new int[] {0};
    final ArrayList<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    DaemonCodeAnalyzerImpl.processHighlights(
      document, project, HighlightSeverity.WARNING, 0, sequence.length(),
      new Processor<HighlightInfo>() {
        @Override
        public boolean process(HighlightInfo info) {
          if (info.severity != HighlightSeverity.WARNING && info.severity != HighlightSeverity.ERROR) return true;
          offset[0] = appendInfo(info, sb, sequence, offset[0], infos);
          return true;
        }
      });
    offset[0] = appendInfo(null, sb, sequence, offset[0], infos);
    sb.append(sequence.subSequence(offset[0], sequence.length()));
    document.setText(sb);
  }

  private static int appendInfo(@Nullable HighlightInfo info, StringBuilder sb, CharSequence sequence, int offset, ArrayList<HighlightInfo> infos) {
    if (info == null || !infos.isEmpty() && infos.get(infos.size() - 1).getEndOffset() < info.getStartOffset()) {
      if (infos.size() == 1) {
        HighlightInfo cur = infos.remove(0);
        sb.append(sequence.subSequence(offset, cur.getStartOffset()));
        sb.append(cur.severity == HighlightSeverity.WARNING ? "<warning>" : "<error>");
        sb.append(sequence.subSequence(cur.getStartOffset(), cur.getEndOffset()));
        sb.append(cur.severity == HighlightSeverity.WARNING ? "</warning>" : "</error>");
        offset = cur.getEndOffset();
      }
      else {
        // process overlapped
        LinkedList<HighlightInfo> stack = new LinkedList<HighlightInfo>();
        for (HighlightInfo cur : infos) {
          offset = processStack(stack, sb, sequence, offset, cur.getStartOffset());
          sb.append(sequence.subSequence(offset, cur.getStartOffset()));
          offset = cur.getStartOffset();
          sb.append(cur.severity == HighlightSeverity.WARNING ? "<warning>" : "<error>");
          stack.addLast(cur);
        }
        offset = processStack(stack, sb, sequence, offset, sequence.length());
        infos.clear();
      }
    }
    if (info != null) {
      boolean found = false;
      for (HighlightInfo cur : infos) {
        if (cur.getStartOffset() == info.getStartOffset() && cur.getEndOffset() == info.getEndOffset() && cur.severity == info.severity) {
          found = true;
          break;
        }
      }
      if (!found) infos.add(info);
    }
    return offset;
  }

  private static int processStack(LinkedList<HighlightInfo> stack,
                                  StringBuilder sb,
                                  CharSequence sequence,
                                  int offset,
                                  final int endOffset) {
    if (stack.isEmpty()) return offset;
    for (HighlightInfo cur = stack.peekLast(); cur != null && cur.getEndOffset() <= endOffset; cur = stack.peekLast()) {
      stack.removeLast();
      if (offset <= cur.getEndOffset()) {
        sb.append(sequence.subSequence(offset, cur.getEndOffset()));
      }
      else {
        //System.out.println("Incorrect overlapping infos: " + offset + " > " + cur.getEndOffset());
      }
      offset = cur.getEndOffset();
      sb.append(cur.severity == HighlightSeverity.WARNING ? "</warning>" : "</error>");
    }
    return offset;
  }
}
