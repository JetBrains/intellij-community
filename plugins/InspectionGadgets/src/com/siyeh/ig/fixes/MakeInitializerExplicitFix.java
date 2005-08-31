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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public class MakeInitializerExplicitFix extends InspectionGadgetsFix{
    public String getName(){
        return InspectionGadgetsBundle.message("make.initialization.explicit.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException{
        final PsiElement fieldName = descriptor.getPsiElement();
        final PsiField field = (PsiField) fieldName.getParent();
        final PsiManager psiManager = PsiManager.getInstance(project);
        final PsiElementFactory factory =
                psiManager.getElementFactory();
        assert field != null;
        final PsiModifierList modifiers = field.getModifierList();
        final PsiType type = field.getType();
        final String name = field.getName();
        final PsiClass containingClass = field.getContainingClass();
        final String newFieldText =
                modifiers.getText() + ' ' + type.getPresentableText() +
                ' ' + name +
                '=' + getDefaultValue(type) + ';';
        final PsiField newField = factory
                .createFieldFromText(newFieldText, containingClass);
        final CodeStyleManager styleManager =
                psiManager.getCodeStyleManager();
        final PsiElement replacedField = field.replace(newField);
        styleManager.reformat(replacedField);
    }

    @NonNls private String getDefaultValue(PsiType type){
        if(PsiType.INT.equals(type)){
            return "0";
        } else if(PsiType.LONG.equals(type)){
            return "0L";
        } else if(PsiType.DOUBLE.equals(type)){
            return "0.0";
        } else if(PsiType.FLOAT.equals(type)){
            return "0.0F";
        } else if(PsiType.SHORT.equals(type)){
            return "(short)0";
        } else if(PsiType.BYTE.equals(type)){
            return "(byte)0";
        } else if(PsiType.BOOLEAN.equals(type)){
          return PsiKeyword.FALSE;
        } else if(PsiType.CHAR.equals(type)){
            return "(char)0";
        }
      return PsiKeyword.NULL;
    }
}
