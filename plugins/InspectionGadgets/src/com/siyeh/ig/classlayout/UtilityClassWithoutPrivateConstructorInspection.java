/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class UtilityClassWithoutPrivateConstructorInspection extends UtilityClassWithoutPrivateConstructorInspectionBase {

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel annotationsPanel = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    panel.add(annotationsPanel, BorderLayout.CENTER);
    final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message("utility.class.without.private.constructor.option"),
                                           this, "ignoreClassesWithOnlyMain");
    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>();
    final PsiClass aClass = (PsiClass)infos[0];
    final PsiMethod constructor = getNullArgConstructor(aClass);
    if (constructor == null) {
      fixes.add(new CreateEmptyPrivateConstructor());
    }
    else {
      final Query<PsiReference> query = ReferencesSearch.search(constructor, constructor.getUseScope());
      final PsiReference reference = query.findFirst();
      if (reference == null) {
        fixes.add(new MakeConstructorPrivateFix());
      }
    }
    AddToIgnoreIfAnnotatedByListQuickFix.build(aClass, ignorableAnnotations, fixes);
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
  }

  protected static class CreateEmptyPrivateConstructor extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.create.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      final Query<PsiReference> query = ReferencesSearch.search(aClass, aClass.getUseScope());
      for (PsiReference reference : query) {
        if (reference == null) {
          continue;
        }
        final PsiElement element = reference.getElement();
        final PsiElement context = element.getParent();
        if (context instanceof PsiNewExpression) {
          SwingUtilities.invokeLater(() -> Messages.showInfoMessage(aClass.getProject(),
                                                                "Utility class has instantiations, private constructor will not be created",
                                                                "Can't generate constructor"));
          return;
        }
      }
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = psiFacade.getElementFactory();
      final PsiMethod constructor = factory.createConstructor();
      final PsiModifierList modifierList = constructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      aClass.add(constructor);
      final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
      styleManager.reformat(constructor);
    }
  }

  private static class MakeConstructorPrivateFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.make.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement classNameIdentifier = descriptor.getPsiElement();
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)parent;
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          final PsiModifierList modifiers = constructor.getModifierList();
          modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
          modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
          modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
        }
      }
    }
  }
}
