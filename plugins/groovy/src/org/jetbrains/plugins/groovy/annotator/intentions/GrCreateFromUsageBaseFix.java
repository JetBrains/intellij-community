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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public abstract class GrCreateFromUsageBaseFix extends Intention {
  protected final SmartPsiElementPointer<GrReferenceExpression> myRefExpression;

  public GrCreateFromUsageBaseFix(@NotNull GrReferenceExpression refExpression) {
    myRefExpression = SmartPointerManager.getInstance(refExpression.getProject()).createSmartPsiElementPointer(refExpression);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  protected GrReferenceExpression getRefExpr() {
    return myRefExpression.getElement();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final GrReferenceExpression element = myRefExpression.getElement();
    if (element == null || !element.isValid()) {
      return false;
    }

    List<PsiClass> targetClasses = getTargetClasses();
    return !targetClasses.isEmpty();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }


  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final List<PsiClass> classes = getTargetClasses();
    if (classes.size() == 1) {
      invokeImpl(project, classes.get(0));
    }
    else if (!classes.isEmpty()) {
      chooseClass(classes, editor);
    }
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        return element instanceof GrReferenceExpression;
      }
    };
  }

  private void chooseClass(List<PsiClass> classes, Editor editor) {
    final Project project = classes.get(0).getProject();

    final JList list = new JBList(classes);
    PsiElementListCellRenderer renderer = PsiClassListCellRenderer.INSTANCE;
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    renderer.installSpeedSearch(builder);

    Runnable runnable = () -> {
      int index = list.getSelectedIndex();
      if (index < 0) return;
      final PsiClass aClass = (PsiClass)list.getSelectedValue();
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> invokeImpl(project, aClass)), getText(), null);
    };

    builder.
      setTitle(QuickFixBundle.message("target.class.chooser.title")).
      setItemChoosenCallback(runnable).
      createPopup().
      showInBestPositionFor(editor);
  }

  protected abstract void invokeImpl(Project project, @NotNull PsiClass targetClass);

  private List<PsiClass> getTargetClasses() {
    final GrReferenceExpression ref = getRefExpr();
    final boolean compileStatic = PsiUtil.isCompileStatic(ref) || GrStaticChecker.isPropertyAccessInStaticMethod(ref);
    final PsiClass targetClass = QuickfixUtil.findTargetClass(ref, compileStatic);
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
