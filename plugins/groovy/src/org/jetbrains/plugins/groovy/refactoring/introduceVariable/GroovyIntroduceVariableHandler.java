/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyIntroduceVariableHandler extends GroovyIntroduceVariableBase {

  protected void showErrorMessage(String message, final Project project) {
    CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.INTRODUCE_VARIABLE, project);
  }

  protected boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  protected GroovyIntroduceVariableSettings getSettings(final Project project, Editor editor, GrExpression expr,
                                                        PsiType exprType, PsiElement[] occurrences, boolean declareFinal,
                                                        Validator validator) {

    // Add occurences highlighting
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, attributes, true, highlighters);
      }
    }

    String[] possibleNames = GroovyNameSuggestionUtil.suggestVariableNames(expr, validator);
    GroovyIntroduceVariableDialog dialog = new GroovyIntroduceVariableDialog(project, expr, exprType, occurrences.length, validator, possibleNames);
    dialog.show();
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    } else {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  protected void highlightOccurences(final Project project, Editor editor, final PsiElement[] elements) {
    GroovyRefactoringUtil.highlightOccurrences(project, editor, elements);
  }

}
