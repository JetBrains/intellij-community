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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class MethodMayBeStaticInspection extends MethodInspection{
    /**
     * @noinspection PublicField
     */
    public boolean m_onlyPrivateOrFinal = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreEmptyMethods = true;
    private final MethodMayBeStaticFix fix = new MethodMayBeStaticFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("method.may.be.static.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("method.may.be.static.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class MethodMayBeStaticFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("make.static.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiJavaToken classNameToken = (PsiJavaToken)
                    descriptor.getPsiElement();
            final PsiMethod innerClass = (PsiMethod) classNameToken.getParent();
            assert innerClass != null;
            final PsiModifierList modifiers = innerClass.getModifierList();
            modifiers.setModifierProperty(PsiModifier.STATIC, true);
        }
    }

    public JComponent createOptionsPanel(){
        final JPanel panel = new JPanel(new GridBagLayout());
        final JCheckBox ignoreFieldAccessesCheckBox =
                new JCheckBox(InspectionGadgetsBundle.message("method.may.be.static.only.option"),
                        m_onlyPrivateOrFinal);
        final ButtonModel ignoreFieldAccessesModel =
                ignoreFieldAccessesCheckBox.getModel();
        ignoreFieldAccessesModel.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e){
                m_onlyPrivateOrFinal = ignoreFieldAccessesModel.isSelected();
            }
        });
        final JCheckBox ignoreEmptyMethodsCheckBox =
                new JCheckBox(InspectionGadgetsBundle.message("method.may.be.static.empty.option"), m_ignoreEmptyMethods);
        final ButtonModel ignoreEmptyMethodsModel =
                ignoreEmptyMethodsCheckBox.getModel();
        ignoreEmptyMethodsModel.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e){
                m_ignoreEmptyMethods = ignoreEmptyMethodsModel.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(ignoreFieldAccessesCheckBox, constraints);
        constraints.gridy = 1;
        panel.add(ignoreEmptyMethodsCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MethodCanBeStaticVisitor();
    }

    private class MethodCanBeStaticVisitor extends BaseInspectionVisitor{
      @NonNls private static final String TEST_METHOD_PREFIX = "test";

      public void visitMethod(@NotNull PsiMethod method){
          super.visitMethod(method);
          if(method.hasModifierProperty(PsiModifier.STATIC)){
              return;
          }
          if(method.isConstructor()){
              return;
          }
          if(method.hasModifierProperty(PsiModifier.ABSTRACT)){
              return;
          }
          if(m_ignoreEmptyMethods && MethodUtils.isEmpty(method)){
              return;
          }
          final PsiClass containingClass =
                  ClassUtils.getContainingClass(method);
          if(containingClass == null){
              return;
          }
          final PsiElement scope = containingClass.getScope();
          if(!(scope instanceof PsiJavaFile) &&
             !containingClass.hasModifierProperty(PsiModifier.STATIC)){
              return;
          }
          if(m_onlyPrivateOrFinal &&
             !method.hasModifierProperty(PsiModifier.FINAL) &&
             !method.hasModifierProperty(PsiModifier.PRIVATE)){
              return;
          }
          final String methodName = method.getName();
          if(methodName != null && methodName.startsWith(TEST_METHOD_PREFIX) &&
             ClassUtils.isSubclass(containingClass,
                                   "junit.framework.TestCase")){
              return;
          }
          final PsiMethod[] superMethods = method.findSuperMethods();
          if(superMethods.length > 0){
              return;
          }
          if(MethodUtils.isOverridden(method)){
              return;
          }
          final MethodReferenceVisitor visitor =
                  new MethodReferenceVisitor(method);
          method.accept(visitor);
          if(!visitor.areReferencesStaticallyAccessible()){
              return;
          }
          registerMethodError(method);
      }
    }
}