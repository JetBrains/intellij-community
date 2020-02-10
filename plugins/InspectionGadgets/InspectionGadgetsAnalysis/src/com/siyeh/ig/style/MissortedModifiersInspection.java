/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class MissortedModifiersInspection extends BaseInspection implements CleanupLocalInspectionTool{

  /**
   * @noinspection PublicField
   */
  public boolean m_requireAnnotationsFirst = true;

  public boolean typeUseWithType = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("missorted.modifiers.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissortedModifiersVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SortModifiersFix();
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JCheckBox box =
      panel.addCheckboxEx(InspectionGadgetsBundle.message("missorted.modifiers.require.option"), "m_requireAnnotationsFirst");
    panel.addDependentCheckBox("Target TYPE_USE annotations always go before type", "typeUseWithType", box);
    return panel;
  }

  private class SortModifiersFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiModifierList modifierList = (PsiModifierList)descriptor.getPsiElement();
      final List<String> modifiers = new SmartList<>();
      final List<String> typeAnnotations = new SmartList<>();
      final List<String> annotations = new SmartList<>();
      for (final PsiElement child : modifierList.getChildren()) {
        if (child instanceof PsiJavaToken) {
          modifiers.add(child.getText());
        }
        else if (child instanceof PsiAnnotation) {
          final PsiAnnotation annotation = (PsiAnnotation)child;
          if (PsiImplUtil.isTypeAnnotation(child) && !isMethodWithVoidReturnType(modifierList.getParent())) {
            final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
            if (typeUseWithType || !modifiers.isEmpty() ||
                AnnotationTargetUtil.findAnnotationTarget(annotation, targets[0]) == PsiAnnotation.TargetType.UNKNOWN) {
              typeAnnotations.add(child.getText());
              continue;
            }
          }
          annotations.add(child.getText());
        }
      }
      Collections.sort(modifiers, new ModifierComparator());
      @NonNls final StringBuilder buffer = new StringBuilder();
      for (String annotation : annotations) {
        buffer.append(annotation).append(' ');
      }
      for (String modifier : modifiers) {
        buffer.append(modifier).append(' ');
      }
      for (String annotation : typeAnnotations) {
        buffer.append(annotation).append(' ');
      }
      buffer.append("void x() {}");
      final PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText(buffer.toString(), modifierList);
      final PsiModifierList newModifierList = method.getModifierList();
      new CommentTracker().replaceAndRestoreComments(modifierList, newModifierList);
    }
  }

  private class MissortedModifiersVisitor extends BaseInspectionVisitor {

    private final Comparator<String> modifierComparator = new ModifierComparator();

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      checkForMissortedModifiers(aClass);
    }

    @Override
    public void visitClassInitializer(
      @NotNull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      checkForMissortedModifiers(initializer);
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      checkForMissortedModifiers(variable);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      checkForMissortedModifiers(parameter);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      checkForMissortedModifiers(method);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      checkForMissortedModifiers(field);
    }

    private void checkForMissortedModifiers(PsiModifierListOwner listOwner) {
      final PsiModifierList modifierList = listOwner.getModifierList();
      if (modifierList == null) {
        return;
      }
      if (!isModifierListMissorted(modifierList)) {
        return;
      }
      registerError(modifierList);
    }

    private boolean isModifierListMissorted(PsiModifierList modifierList) {
      if (modifierList == null) {
        return false;
      }
      String currentModifier = null;
      boolean typeAnnotationSeen = false;
      for (final PsiElement child : modifierList.getChildren()) {
        if (child instanceof PsiJavaToken) {
          if (typeAnnotationSeen) return true;
          final String text = child.getText();
          if (modifierComparator.compare(text, currentModifier) < 0) {
            return true;
          }
          currentModifier = text;
        }
        if (child instanceof PsiAnnotation) {
          if (m_requireAnnotationsFirst) {
            final PsiAnnotation annotation = (PsiAnnotation)child;
            if (AnnotationTargetUtil.isTypeAnnotation(annotation) && !isMethodWithVoidReturnType(modifierList.getParent())) {
              // type annotations go next to the type
              // see e.g. https://www.oracle.com/technical-resources/articles/java/ma14-architect-annotations.html
              if (typeUseWithType || currentModifier != null) {
                typeAnnotationSeen = true;
              }
              final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
              if (AnnotationTargetUtil.findAnnotationTarget(annotation, targets[0]) == PsiAnnotation.TargetType.UNKNOWN) {
                typeAnnotationSeen = true;
              }
              continue;
            }
            if (m_requireAnnotationsFirst && currentModifier != null) {
              //things aren't in order, since annotations come first
              return true;
            }
          }
          else if (currentModifier != null) {
            typeAnnotationSeen = true;
          }
        }
      }
      return false;
    }
  }

  static boolean isMethodWithVoidReturnType(PsiElement element) {
    return element instanceof PsiMethod && PsiType.VOID.equals(((PsiMethod)element).getReturnType());
  }

  private static class ModifierComparator implements Comparator<String> {

    @NonNls private static final Map<String, Integer> s_modifierOrder = new HashMap<>(12);

    static {
      s_modifierOrder.put(PsiModifier.PUBLIC, Integer.valueOf(0));
      s_modifierOrder.put(PsiModifier.PROTECTED, Integer.valueOf(1));
      s_modifierOrder.put(PsiModifier.PRIVATE, Integer.valueOf(2));
      s_modifierOrder.put(PsiModifier.ABSTRACT, Integer.valueOf(3));
      s_modifierOrder.put(PsiModifier.DEFAULT, Integer.valueOf(4));
      s_modifierOrder.put(PsiModifier.STATIC, Integer.valueOf(5));
      s_modifierOrder.put(PsiModifier.FINAL, Integer.valueOf(6));
      s_modifierOrder.put(PsiModifier.TRANSIENT, Integer.valueOf(7));
      s_modifierOrder.put(PsiModifier.VOLATILE, Integer.valueOf(8));
      s_modifierOrder.put(PsiModifier.SYNCHRONIZED, Integer.valueOf(9));
      s_modifierOrder.put(PsiModifier.NATIVE, Integer.valueOf(10));
      s_modifierOrder.put(PsiModifier.STRICTFP, Integer.valueOf(11));
    }

    @Override
    public int compare(String modifier1, String modifier2) {
      final Integer ordinal1 = s_modifierOrder.get(modifier1);
      final Integer ordinal2 = s_modifierOrder.get(modifier2);
      return (ordinal1 == null || ordinal2 == null) ? 0 : ordinal1.intValue() - ordinal2.intValue();
    }
  }
}