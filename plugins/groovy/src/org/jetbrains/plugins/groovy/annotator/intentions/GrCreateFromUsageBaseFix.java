// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class GrCreateFromUsageBaseFix extends Intention {
  @SafeFieldForPreview // all inheritors handle preview
  protected final SmartPsiElementPointer<GrReferenceExpression> myRefExpression;

  protected GrCreateFromUsageBaseFix(@NotNull GrReferenceExpression refExpression) {
    myRefExpression = SmartPointerManager.getInstance(refExpression.getProject()).createSmartPsiElementPointer(refExpression);
  }

  protected GrReferenceExpression getRefExpr() {
    return myRefExpression.getElement();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    final GrReferenceExpression element = myRefExpression.getElement();
    if (element == null || !element.isValid()) {
      return false;
    }

    List<PsiClass> targetClasses = getTargetClasses();
    return !targetClasses.isEmpty();
  }


  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final List<PsiClass> classes = getTargetClasses();
    if (classes.size() == 1) {
      invokeImpl(project, classes.get(0));
    }
    else if (!classes.isEmpty()) {
      chooseClass(classes, editor);
    }
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        return element instanceof GrReferenceExpression;
      }
    };
  }

  private void chooseClass(List<PsiClass> classes, Editor editor) {
    final Project project = classes.get(0).getProject();
    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    final IPopupChooserBuilder<PsiClass> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(classes)
      .setRenderer(renderer)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setItemChosenCallback((aClass) -> CommandProcessor.getInstance()
                                                         .executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> invokeImpl(project, aClass)), getText(),
                        null))
      .setTitle(QuickFixBundle.message("target.class.chooser.title"));
    renderer.installSpeedSearch(builder);
    builder.createPopup().showInBestPositionFor(editor);
  }

  protected abstract void invokeImpl(Project project, @NotNull PsiClass targetClass);

  protected List<PsiClass> getTargetClasses() {
    final GrReferenceExpression ref = getRefExpr();
    final PsiClass targetClass = QuickfixUtil.findTargetClass(ref);
    if (targetClass == null || !canBeTargetClass(targetClass)) return Collections.emptyList();

    final ArrayList<PsiClass> classes = new ArrayList<>();
    collectSupers(targetClass, classes);
    return classes;
  }

  private void collectSupers(PsiClass psiClass, ArrayList<PsiClass> classes) {
    classes.add(psiClass);

    final PsiClass[] supers = psiClass.getSupers();
    for (PsiClass aSuper : supers) {
      if (classes.contains(aSuper)) continue;
      if (canBeTargetClass(aSuper)) {
        collectSupers(aSuper, classes);
      }
    }
  }

  protected boolean canBeTargetClass(PsiClass psiClass) {
    return psiClass.getManager().isInProject(psiClass);
  }
}
