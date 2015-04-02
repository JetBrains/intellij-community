/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions;

import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.flow.GrNullness;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase;

import java.util.List;

public class GrGetNullabilityAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) return;

    final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
    if (!(psiFile instanceof GroovyFile)) return;

    int offset = editor.getCaretModel().getOffset();

    List<GrExpression> expressions = GrIntroduceHandlerBase.collectExpressions(psiFile, editor, offset, true);
    if (expressions.isEmpty()) return;
    if (expressions.size() == 1) {
      passInner(expressions.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(editor, expressions, new Pass<GrExpression>() {
        @Override
        public void pass(GrExpression grExpression) {
          passInner(grExpression);
        }
      }, new Function<GrExpression, String>() {
        @Override
        public String fun(GrExpression grExpression) {
          return grExpression.getText();
        }
      });
    }
  }


  public static void passInner(GrExpression expression) {
    final Nullness nullness = GrNullness.getNullability(expression);
    GrGetPsiTypeAction.showBalloon(expression.getProject(), StringUtil.escapeXml(nullness + ": " + expression.getText()), MessageType.INFO);
  }

  @Override
  public void update(AnActionEvent e) {
    Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor != null) {
      PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
      if (psiFile instanceof GroovyFile) {
        e.getPresentation().setEnabled(true);
        return;
      }
    }
    e.getPresentation().setEnabled(false);
  }
}

