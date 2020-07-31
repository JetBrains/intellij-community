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
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MissortedModifiersInspection extends BaseInspection implements CleanupLocalInspectionTool{

  /**
   * @noinspection PublicField
   */
  public boolean m_requireAnnotationsFirst = true;

  public boolean typeUseWithType = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiModifierList modifierList = (PsiModifierList)infos[0];
    final List<String> modifiers = getModifiers(modifierList);
    final List<String> sortedModifiers = getSortedModifiers(modifierList);
    final List<String> missortedModifiers = stripCommonPrefixSuffix(modifiers, sortedModifiers);
    return InspectionGadgetsBundle.message("missorted.modifiers.problem.descriptor", String.join(" ", missortedModifiers));
  }

  private static <E> List<E> stripCommonPrefixSuffix(List<E> list1, List<E> list2) {
    final int max = list1.size() - commonSuffixLength(list1, list2);
    final List<E> result = new SmartList<>();
    for (int i = 0; i < max; i++) {
      final E token = list1.get(i);
      if (token.equals(list2.get(i))) continue; // common prefix
      result.add(token);
    }
    return result;
  }

  @Contract(pure = true)
  private static <E> int commonSuffixLength(@NotNull List<E> l1, @NotNull List<E> l2) {
    final int size1 = l1.size();
    final int size2 = l2.size();
    if (size1 == 0 || size2 == 0) return 0;
    int i = 0;
    for (; i < size1 && i < size2; i++) {
      if (!l1.get(size1 - i - 1).equals(l2.get(size2 - i - 1))) {
        break;
      }
    }
    return i;
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
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "typeUseWithType");
    writeBooleanOption(node, "typeUseWithType", false);
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    final JCheckBox box = panel.addCheckboxEx(InspectionGadgetsBundle.message("missorted.modifiers.require.option"),
                                              "m_requireAnnotationsFirst");
    panel.addDependentCheckBox(InspectionGadgetsBundle.message("missorted.modifiers.typeuse.before.type.option"), "typeUseWithType", box);
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
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiModifierList)) {
        element = element.getParent();
        if (!(element instanceof PsiModifierList)) return;
      }
      final PsiModifierList modifierList = (PsiModifierList)element;
      @NonNls final String text = String.join(" ", getSortedModifiers(modifierList));
      PsiModifierList newModifierList = createNewModifierList(modifierList, text);
      if (newModifierList != null) {
        new CommentTracker().replaceAndRestoreComments(modifierList, newModifierList);
      }
    }

    @Nullable
    private PsiModifierList createNewModifierList(@NotNull PsiModifierList oldModifierList, @NotNull String newModifiersText) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(oldModifierList.getProject());
      if (oldModifierList.getParent() instanceof PsiRequiresStatement) {
        String text = "requires " + newModifiersText + " x;";
        PsiRequiresStatement statement = (PsiRequiresStatement) factory.createModuleStatementFromText(text, oldModifierList);
        return statement.getModifierList();
      } else {
        PsiMethod method = factory.createMethodFromText(newModifiersText + " void x() {}", oldModifierList);
        return method.getModifierList();
      }
    }
  }

  private static List<String> getModifiers(PsiModifierList modifierList) {
    return Stream.of(modifierList.getChildren())
      .filter(e -> e instanceof PsiJavaToken || e instanceof PsiAnnotation)
      .map(PsiElement::getText)
      .collect(Collectors.toList());
  }

  private List<String> getSortedModifiers(PsiModifierList modifierList) {
    final List<String> modifiers = new SmartList<>();
    final List<String> typeAnnotations = new SmartList<>();
    final List<String> annotations = new SmartList<>();
    for (PsiElement child : modifierList.getChildren()) {
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
    modifiers.sort(new ModifierComparator());
    final List<String> result = new SmartList<>();
    result.addAll(annotations);
    result.addAll(modifiers);
    result.addAll(typeAnnotations);
    return result;
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

    @Override
    public void visitRequiresStatement(PsiRequiresStatement statement) {
      super.visitRequiresStatement(statement);
      checkForMissortedModifiers(statement);
    }

    private void checkForMissortedModifiers(PsiModifierListOwner listOwner) {
      final PsiModifierList modifierList = listOwner.getModifierList();
      if (modifierList == null) {
        return;
      }
      final PsiElement modifier = getFirstMisorderedModifier(modifierList);
      if (modifier == null) {
        return;
      }
      registerError(isVisibleHighlight(modifierList) ? modifier : modifierList, modifierList);
    }

    private PsiElement getFirstMisorderedModifier(PsiModifierList modifierList) {
      if (modifierList == null) {
        return null;
      }
      final Deque<PsiElement> modifiers = new ArrayDeque<>();
      PsiAnnotation typeAnnotation = null;
      for (final PsiElement child : modifierList.getChildren()) {
        if (child instanceof PsiJavaToken) {
          if (typeAnnotation != null) return typeAnnotation;
          final String text = child.getText();
          if (!modifiers.isEmpty() && modifierComparator.compare(text, modifiers.getLast().getText()) < 0) {
            while (!modifiers.isEmpty()) {
              final PsiElement first = modifiers.pollFirst();
              if (modifierComparator.compare(text, first.getText()) < 0) {
                return first;
              }
            }
          }
          modifiers.add(child);
        }
        if (child instanceof PsiAnnotation) {
          final PsiAnnotation annotation = (PsiAnnotation)child;
          if (m_requireAnnotationsFirst) {
            if (AnnotationTargetUtil.isTypeAnnotation(annotation) && !isMethodWithVoidReturnType(modifierList.getParent())) {
              // type annotations go next to the type
              // see e.g. https://www.oracle.com/technical-resources/articles/java/ma14-architect-annotations.html
              if (typeUseWithType || !modifiers.isEmpty()) {
                typeAnnotation = annotation;
              }
              final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
              if (AnnotationTargetUtil.findAnnotationTarget(annotation, targets[0]) == PsiAnnotation.TargetType.UNKNOWN) {
                typeAnnotation = annotation;
              }
              continue;
            }
            if (m_requireAnnotationsFirst && !modifiers.isEmpty()) {
              //things aren't in order, since annotations come first
              return modifiers.getFirst();
            }
          }
          else if (!modifiers.isEmpty()) {
            typeAnnotation = annotation;
          }
        }
      }
      return null;
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
      s_modifierOrder.put(PsiModifier.TRANSITIVE, Integer.valueOf(12));
    }

    @Override
    public int compare(String modifier1, String modifier2) {
      final Integer ordinal1 = s_modifierOrder.get(modifier1);
      final Integer ordinal2 = s_modifierOrder.get(modifier2);
      return (ordinal1 == null || ordinal2 == null) ? 0 : ordinal1.intValue() - ordinal2.intValue();
    }
  }
}