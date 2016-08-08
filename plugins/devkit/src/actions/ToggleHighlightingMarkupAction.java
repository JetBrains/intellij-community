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

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.IndentsPass;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gregsh
 */
public class ToggleHighlightingMarkupAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    e.getPresentation().setEnabled(editor != null && file != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (editor == null || file == null) return;
    final Project project = file.getProject();
    CommandProcessorEx commandProcessor = (CommandProcessorEx)CommandProcessorEx.getInstance();
    Object commandToken = commandProcessor.startCommand(project, e.getPresentation().getText(), e.getPresentation().getText(), UndoConfirmationPolicy.DEFAULT);
    AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
    try {
      final SelectionModel selectionModel = editor.getSelectionModel();
      int[] starts = selectionModel.getBlockSelectionStarts();
      int[] ends = selectionModel.getBlockSelectionEnds();

      int startOffset = starts.length == 0? 0 : starts[0];
      int endOffset = ends.length == 0? editor.getDocument().getTextLength() : ends[ends.length - 1];

      perform(project, editor.getDocument(), startOffset, endOffset);
    }
    finally {
      token.finish();
      commandProcessor.finishCommand(project, commandToken, null);
    }
  }

  private static void perform(Project project, final Document document, final int startOffset, final int endOffset) {
    final CharSequence sequence = document.getCharsSequence();
    final StringBuilder sb = new StringBuilder();
    Pattern pattern = Pattern.compile("<(error|warning|EOLError|EOLWarning|info|weak_warning)((?:\\s|=|\\w+|\\\"(?:[^\"]|\\\\\\\")*?\\\")*?)>(.*?)</\\1>");
    Matcher matcher = pattern.matcher(sequence);
    List<TextRange> ranges = new ArrayList<>();
    if (matcher.find(startOffset)) {
      boolean compactMode = false;
      int pos;
      do {
        if (matcher.start(0) >= endOffset) break;
        if (matcher.start(2) < matcher.end(2)) {
          if (!compactMode) {
            ranges.clear();
            compactMode = true;
          }
          ranges.add(new TextRange(matcher.start(2), matcher.end(2)));
        }
        else if (!compactMode) {
          ranges.add(new TextRange(matcher.start(0), matcher.start(3)));
          ranges.add(new TextRange(matcher.end(3), matcher.end(0)));
        }
        pos = Math.max(matcher.end(1), matcher.end(2));
      }
      while (matcher.find(pos));
      Collections.sort(ranges, IndentsPass.RANGE_COMPARATOR);
    }
    if (!ranges.isEmpty()) {
      int pos = 0;
      for (TextRange range : ranges) {
        sb.append(sequence, pos, range.getStartOffset());
        pos = range.getEndOffset();
      }
      sb.append(sequence, pos, sequence.length());
    }
    else {
      final int[] offset = new int[] {0};
      final ArrayList<HighlightInfo> infos = new ArrayList<>();
      DaemonCodeAnalyzerEx.processHighlights(
        document, project, HighlightSeverity.WARNING, 0, sequence.length(),
        info -> {
          if (info.getSeverity() != HighlightSeverity.WARNING && info.getSeverity() != HighlightSeverity.ERROR) return true;
          if (info.getStartOffset() >= endOffset) return false;
          if (info.getEndOffset() > startOffset) {
            offset[0] = appendInfo(info, sb, sequence, offset[0], infos, false);
          }
          return true;
        });
      offset[0] = appendInfo(null, sb, sequence, offset[0], infos, false);
      sb.append(sequence.subSequence(offset[0], sequence.length()));
    }
    document.setText(sb);
  }

  private static int appendInfo(@Nullable HighlightInfo info,
                                StringBuilder sb,
                                CharSequence sequence,
                                int offset,
                                ArrayList<HighlightInfo> infos, final boolean compact) {
    if (info == null || !infos.isEmpty() && getMaxEnd(infos) < info.getStartOffset()) {
      if (infos.size() == 1) {
        HighlightInfo cur = infos.remove(0);
        sb.append(sequence.subSequence(offset, cur.getStartOffset()));
        appendTag(sb, cur, true, compact);
        sb.append(sequence.subSequence(cur.getStartOffset(), cur.getEndOffset()));
        appendTag(sb, cur, false, compact);
        offset = cur.getEndOffset();
      }
      else {
        // process overlapped
        LinkedList<HighlightInfo> stack = new LinkedList<>();
        for (HighlightInfo cur : infos) {
          offset = processStack(stack, sb, sequence, offset, cur.getStartOffset(), compact);
          sb.append(sequence.subSequence(offset, cur.getStartOffset()));
          offset = cur.getStartOffset();
          appendTag(sb, cur, true, compact);
          stack.addLast(cur);
        }
        offset = processStack(stack, sb, sequence, offset, sequence.length(), compact);
        infos.clear();
      }
    }
    if (info != null) {
      boolean found = false;
      for (HighlightInfo cur : infos) {
        if (cur.getStartOffset() == info.getStartOffset() && cur.getEndOffset() == info.getEndOffset() && cur.getSeverity() ==
                                                                                                          info.getSeverity()) {
          found = true;
          break;
        }
      }
      if (!found) infos.add(info);
    }
    return offset;
  }

  private static int getMaxEnd(ArrayList<HighlightInfo> infos) {
    int max = -1;
    for (HighlightInfo info : infos) {
      int endOffset = info.getEndOffset();
      if (max < endOffset) max = endOffset;
    }
    return max;
  }

  private static int processStack(LinkedList<HighlightInfo> stack,
                                  StringBuilder sb,
                                  CharSequence sequence,
                                  int offset,
                                  final int endOffset,
                                  final boolean compact) {
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
      appendTag(sb, cur, false, compact);
    }
    return offset;
  }

  private static void appendTag(StringBuilder sb, HighlightInfo cur, boolean opening, final boolean compact) {
    sb.append("<");
    if (!opening) sb.append("/");
    if (cur.isAfterEndOfLine()) {
      sb.append(cur.getSeverity() == HighlightSeverity.WARNING ? "EOLWarning" : "EOLError");
    }
    else {
      sb.append(cur.getSeverity() == HighlightSeverity.WARNING ? "warning" : "error");
    }
    if (opening && !compact) {
      sb.append(" descr=\"").append(cur.getDescription()).append("\"");

    }
    sb.append(">");
  }
}
