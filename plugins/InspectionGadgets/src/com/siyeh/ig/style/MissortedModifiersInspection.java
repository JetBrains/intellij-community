/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;

import javax.swing.*;
import java.util.*;

import org.jetbrains.annotations.NonNls;

public class MissortedModifiersInspection extends BaseInspection {

  private static final int NUM_MODIFIERS = 11;
  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Map<String, Integer> s_modifierOrder = new HashMap<String, Integer>(NUM_MODIFIERS);
  public boolean m_requireAnnotationsFirst = true;
  private final SortModifiersFix fix = new SortModifiersFix();

  static {
    s_modifierOrder.put("public", 0);
    s_modifierOrder.put("protected", 1);
    s_modifierOrder.put("private", 2);
    s_modifierOrder.put("static", 3);
    s_modifierOrder.put("abstract", 4);
    s_modifierOrder.put("final", 5);
    s_modifierOrder.put("transient", 6);
    s_modifierOrder.put("volatile", 7);
    s_modifierOrder.put("synchronized", 8);
    s_modifierOrder.put("native", 9);
    s_modifierOrder.put("strictfp", 10);
  }

  public String getGroupDisplayName() {
    return GroupNames.STYLE_GROUP_NAME;
  }

  public ProblemDescriptor[] doCheckClass(PsiClass aClass,
                                          InspectionManager manager,
                                          boolean isOnTheFly) {
    if (!aClass.isPhysical()) {
      return super.doCheckClass(aClass, manager, isOnTheFly);
    }
    return checkModifierListOwner(aClass, manager);
  }

  public ProblemDescriptor[] doCheckMethod(PsiMethod method,
                                           InspectionManager manager,
                                           boolean isOnTheFly) {
    if (!method.isPhysical()) {
      return super.doCheckMethod(method, manager, isOnTheFly);
    }
    return checkModifierListOwner(method, manager);
  }

  public ProblemDescriptor[] doCheckField(PsiField field,
                                          InspectionManager manager,
                                          boolean isOnTheFly) {
    if (!field.isPhysical()) {
      return super.doCheckField(field, manager, isOnTheFly);
    }
    return checkModifierListOwner(field, manager);
  }

  private ProblemDescriptor[] checkModifierListOwner(
    PsiModifierListOwner modifierListOwner,
    InspectionManager manager
  ) {
    final PsiModifierList modifierList = modifierListOwner.getModifierList();
    if (!isModifierListMissorted(modifierList)) {
      return null;
    }
    final String description = buildErrorString(modifierList);
    final ProblemDescriptor problemDescriptor =
      manager.createProblemDescriptor(modifierList, description, fix,
                                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    return new ProblemDescriptor[]{problemDescriptor};
  }

  public BaseInspectionVisitor buildVisitor() {
    return null;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("missorted.modifiers.require.option"),
                                          this, "m_requireAnnotationsFirst");
  }

  private static class SortModifiersFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {

      final PsiModifierList modifierList = (PsiModifierList)descriptor.getPsiElement();
      final List<String> simpleModifiers = new ArrayList<String>();
      final PsiElement[] children = modifierList.getChildren();
      for (final PsiElement child : children) {
        if (child instanceof PsiJavaToken) {
          simpleModifiers.add(child.getText());
        }
        if (child instanceof PsiAnnotation) {
        }
      }
      Collections.sort(simpleModifiers, new ModifierComparator());
      clearModifiers(simpleModifiers, modifierList);
      addModifiersInOrder(simpleModifiers, modifierList);
    }

    private static void addModifiersInOrder(List<String> modifiers,
                                            PsiModifierList modifierList)
      throws IncorrectOperationException {
      for (String modifier : modifiers) {
        modifierList.setModifierProperty(modifier, true);
      }
    }

    private static void clearModifiers(List<String> modifiers,
                                       PsiModifierList modifierList)
      throws IncorrectOperationException {
      for (final String modifier : modifiers) {
        modifierList.setModifierProperty(modifier, false);

      }
    }
  }

  private boolean hasMissortedModifierList(PsiModifierListOwner listOwner) {
    final PsiModifierList modifierList = listOwner.getModifierList();
    return isModifierListMissorted(modifierList);
  }

  private boolean isModifierListMissorted(PsiModifierList modifierList) {
    if (modifierList == null) {
      return false;
    }

    final List<PsiElement> simpleModifiers = new ArrayList<PsiElement>();
    final PsiElement[] children = modifierList.getChildren();
    for (final PsiElement child : children) {
      if (child instanceof PsiJavaToken) {
        simpleModifiers.add(child);
      }
      if (child instanceof PsiAnnotation) {
        if (m_requireAnnotationsFirst && simpleModifiers.size() != 0) {
          return true; //things aren't in order, since annotations come first
        }
      }
    }
    int currentModifierIndex = -1;

    for (Object simpleModifier : simpleModifiers) {
      final PsiJavaToken token = (PsiJavaToken)simpleModifier;
      final String text = token.getText();
      final Integer modifierIndex = s_modifierOrder.get(text);
      if (modifierIndex == null) {
        return false;
      }
      if (currentModifierIndex >= modifierIndex) {
        return true;
      }
      currentModifierIndex = modifierIndex;
    }
    return false;
  }
}
