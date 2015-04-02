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

import com.intellij.codeInspection.dataFlow.ControlFlow;
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.value.java.DfaValueFactoryJava;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;

import java.util.ArrayList;
import java.util.List;

public class DumpJavaControlFlowAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
    if (editor == null) return;

    final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
    if (!(psiFile instanceof PsiJavaFile)) return;

    final int offset = editor.getCaretModel().getOffset();
    final PsiElement underCaret = psiFile.findElementAt(offset);
    final List<PsiElement> controlFlowOwners = collectControlFlowOwners(underCaret);
    if (controlFlowOwners.isEmpty()) return;

    IntroduceTargetChooser.showChooser(
      editor, controlFlowOwners, new Pass<PsiElement>() {
        @Override
        public void pass(PsiElement owner) {
          passInner(owner);
        }
      }, new Function<PsiElement, String>() {
        @Override
        public String fun(PsiElement owner) {
          return owner.getText();
        }
      }
    );
  }

  private static List<PsiElement> collectControlFlowOwners(PsiElement underCaret) {
    final List<PsiElement> result = new ArrayList<PsiElement>();
    PsiElement owner = underCaret.getContext();
    while (owner != null) {
      result.add(owner);
      owner = owner.getContext();
    }
    return result;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void passInner(PsiElement owner) {
    ControlFlowAnalyzer analyzer = new ControlFlowAnalyzer(new DfaValueFactoryJava(true, false), false, owner);
    ControlFlow flow = analyzer.buildControlFlow();
    System.out.println(owner.getText());
    System.out.println(flow);
  }
}
