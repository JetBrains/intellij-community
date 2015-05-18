/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.flow.GrControlFlowAnalyzerImpl;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DumpNewGroovyControlFlow extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) return;

    final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
    if (!(psiFile instanceof GroovyFile)) return;

    int offset = editor.getCaretModel().getOffset();
    final GroovyPsiElement underCaret = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), GroovyPsiElement.class);
    final List<GroovyPsiElement> controlFlowOwners = collectControlFlowOwners(underCaret);
    if (controlFlowOwners.isEmpty()) return;
    if (controlFlowOwners.size() == 1) {
      passInner(controlFlowOwners.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor,
        controlFlowOwners,
        new Pass<GroovyPsiElement>() {
          @Override
          public void pass(GroovyPsiElement grExpression) {
            passInner(grExpression);
          }
        }, new Function<GroovyPsiElement, String>() {
          @Override
          public String fun(GroovyPsiElement flowOwner) {
            return flowOwner.getText();
          }
        }
      );
    }
  }

  @NotNull
  private static List<GroovyPsiElement> collectControlFlowOwners(@Nullable GroovyPsiElement underCaret) {
    final List<GroovyPsiElement> result = new ArrayList<GroovyPsiElement>();
    GroovyPsiElement owner = underCaret;
    do {
      owner = PsiTreeUtil.getParentOfType(owner, GrMethod.class, GrClosableBlock.class, GrVariableDeclaration.class, GrParameterList.class);
      if (owner != null) result.add(owner);
    }
    while (owner != null);
    return result;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void passInner(GroovyPsiElement owner) {
    final Collection<GrControlFlowOwner> owners = PsiTreeUtil.findChildrenOfType(owner, GrControlFlowOwner.class);
    if (!owners.isEmpty()) {
      GrControlFlowAnalyzerImpl analyzer = new GrControlFlowAnalyzerImpl(
        new GrDfaValueFactory(owner.getProject(), false), owners.iterator().next()
      );
      ControlFlow flow = analyzer.buildControlFlow();
      System.out.println(owner.getText());
      System.out.println(flow);
    }
  }
}
