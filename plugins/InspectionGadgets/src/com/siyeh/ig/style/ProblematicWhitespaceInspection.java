/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ProblematicWhitespaceInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("problematic.whitespace.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Boolean useTabs = (Boolean)infos[2];
    return useTabs.booleanValue()
           ? InspectionGadgetsBundle.message("problematic.whitespace.spaces.problem.descriptor", (String)infos[0])
           : InspectionGadgetsBundle.message("problematic.whitespace.tabs.problem.descriptor", (String)infos[0]);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean buildFix = ((Boolean)infos[1]).booleanValue();
    if (!buildFix) {
      return null;
    }
    return new ShowWhitespaceFix();
  }


  private static class ShowWhitespaceFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("problematic.whitespace.show.whitespaces.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final FileEditorManager editorManager = FileEditorManager.getInstance(project);
      final Editor editor = editorManager.getSelectedTextEditor();
      if (editor == null) {
        return;
      }
      final EditorSettings settings = editor.getSettings();
      settings.setWhitespacesShown(!settings.isWhitespacesShown());
      editor.getComponent().repaint();
    }
  }


  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ProblematicWhitespaceVisitor();
  }

  private static class ProblematicWhitespaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitJavaFile(PsiJavaFile file) {
      super.visitJavaFile(file);
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(file.getProject());
      final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(JavaFileType.INSTANCE);
      final boolean useTabs = indentOptions.USE_TAB_CHARACTER;
      final boolean smartTabs = indentOptions.SMART_TABS;
      final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      if (document == null) {
        return;
      }
      final int lineCount = document.getLineCount();
      int previousLineIndent = 0;
      for (int i = 0; i < lineCount; i++) {
        final int startOffset = document.getLineStartOffset(i);
        final int endOffset = document.getLineEndOffset(i);
        final String line = document.getText(new TextRange(startOffset, endOffset));
        boolean spaceSeen = false;
        for (int j = 0, length = line.length(); j < length; j++) {
          final char c = line.charAt(j);
          if (c == '\t') {
            if (useTabs) {
              if (smartTabs && spaceSeen) {
                registerError(file, file.getName(), Boolean.valueOf(isOnTheFly()), Boolean.TRUE);
                return;
              }
            }
            else {
              registerError(file, file.getName(), Boolean.valueOf(isOnTheFly()), Boolean.FALSE);
              return;
            }
          }
          else if (c == ' ') {
            if (useTabs) {
              if (!smartTabs) {
                registerError(file, file.getName(), Boolean.valueOf(isOnTheFly()), Boolean.TRUE);
                return;
              }
              else {
                final int currentIndent = Math.max(0, j);
                if (currentIndent != previousLineIndent) {
                  registerError(file, file.getName(), Boolean.valueOf(isOnTheFly()), Boolean.TRUE);
                  return;
                }
                previousLineIndent = currentIndent;
              }
            }
            spaceSeen = true;
          }
          else {
            if (!spaceSeen) {
              previousLineIndent = Math.max(0, j);
            }
            break;
          }
        }
      }
    }
  }
}
