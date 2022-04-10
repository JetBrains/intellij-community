// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.IntroduceTargetChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class DumpGroovyControlFlowAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;

    final PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
    if (!(psiFile instanceof GroovyFile)) return;

    int offset = editor.getCaretModel().getOffset();

    final List<GrControlFlowOwner> controlFlowOwners = collectControlFlowOwners(psiFile, offset);
    if (controlFlowOwners.isEmpty()) return;
    if (controlFlowOwners.size() == 1) {
      passInner(controlFlowOwners.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(editor, controlFlowOwners, new Pass<>() {
                                           @Override
                                           public void pass(GrControlFlowOwner grExpression) {
                                             passInner(grExpression);
                                           }
                                         }, flowOwner -> flowOwner.getText()
      );
    }
  }

  private static List<GrControlFlowOwner> collectControlFlowOwners(final PsiFile file, final int offset) {
    final PsiElement elementAtCaret = file.findElementAt(offset);
    final List<GrControlFlowOwner> result = new ArrayList<>();

    for (GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(elementAtCaret);
         owner != null && !result.contains(owner);
         owner = ControlFlowUtils.findControlFlowOwner(owner)) {
      result.add(owner);
    }
    return result;
  }

  private static void passInner(GrControlFlowOwner owner) {
    System.out.println(owner.getText());
    System.out.println(ControlFlowUtils.dumpControlFlow(ControlFlowUtils.getGroovyControlFlow(owner)));
  }
}
