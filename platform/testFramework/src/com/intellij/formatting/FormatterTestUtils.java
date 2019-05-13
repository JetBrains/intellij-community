/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Assert;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FormatterTestUtils {


  public interface TestFormatAction {
    void run(PsiFile psiFile, int startOffset, int endOffset);
  }
  
  public enum Action {
    REFORMAT, 
    INDENT, 
    REFORMAT_WITH_CONTEXT, 
    REFORMAT_WITH_INSERTED_LINE_CONTEXT
  }
  
  public static final Map<Action, TestFormatAction> ACTIONS = new EnumMap<>(Action.class);
  
  public static class FormatData {
    public int startOffset;
    public int endOffset;
    public String text;

    public FormatData(String text, int startOffset, int endOffset) {
      this.text = text;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }
  }
  
  public static void testFormatting(@NotNull Project project,
                                    @NotNull String ext,
                                    @NotNull String before,
                                    @NotNull String after,
                                    @NotNull Action action) {
    String fileName = "FTU." + ext;
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);

    FormatData data = extractFormatData(before);
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(fileName, fileType, data.text, System.currentTimeMillis(), true);

    PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    Document document = manager.getDocument(file);
    if (document == null) {
      throw new IllegalStateException("Document is null");
    }

    TestFormatAction formatAction = ACTIONS.get(action);
    if (formatAction == null) {
      throw new IllegalStateException("Format action is null");
    }

    WriteCommandAction.runWriteCommandAction(project, () -> formatAction.run(file, data.startOffset, data.endOffset));
    Assert.assertEquals(after, document.getText());
  }

  private static FormatData extractFormatData(@NotNull String before) {
    final String SELECTION_START = "<selection>";
    final String SELECTION_END = "<selection/>";
    
    int startOffset = before.indexOf(SELECTION_START);
    if (startOffset > 0) {
      int endOffset = before.indexOf(SELECTION_END) - SELECTION_START.length();
      String text = before
        .replace(SELECTION_START, "")
        .replace(SELECTION_END, "");
      return new FormatData(text, startOffset, endOffset);
    }
    
    return new FormatData(before, 0, before.length());
  }

  static {
    ACTIONS.put(Action.REFORMAT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        Project project = psiFile.getProject();
        CodeStyleManager.getInstance(project).reformatText(psiFile, startOffset, endOffset);
      }
    });
    
    ACTIONS.put(Action.INDENT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        Project project = psiFile.getProject();
        CodeStyleManager.getInstance(project).adjustLineIndent(psiFile, startOffset);
      }
    });
    
    ACTIONS.put(Action.REFORMAT_WITH_CONTEXT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        List<TextRange> ranges = ContainerUtil.newArrayList(new TextRange(startOffset, endOffset));
        Project project = psiFile.getProject();
        CodeStyleManager.getInstance(project).reformatTextWithContext(psiFile, ranges);
      }
    });
    
    ACTIONS.put(Action.REFORMAT_WITH_INSERTED_LINE_CONTEXT, new TestFormatAction() {
      @Override
      public void run(PsiFile psiFile, int startOffset, int endOffset) {
        List<TextRange> ranges = ContainerUtil.newArrayList(new TextRange(startOffset, endOffset));
        Project project = psiFile.getProject();
        CodeStyleManager.getInstance(project).reformatTextWithContext(psiFile, new ChangedRangesInfo(ranges, ranges));
      }
    });
  }
  
}
