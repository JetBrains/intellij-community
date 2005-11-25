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
package com.siyeh.ig;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseInspectionVisitor extends PsiRecursiveElementVisitor{

    private BaseInspection inspection = null;
    private InspectionManager inspectionManager = null;
    private boolean onTheFly = false;
    private List<ProblemDescriptor> errors = null;
    private boolean classVisited = false;

    public void setInspection(BaseInspection inspection){
        this.inspection = inspection;
    }

    public void setInspectionManager(InspectionManager inspectionManager){
        this.inspectionManager = inspectionManager;
    }

    public void setOnTheFly(boolean onTheFly){
        this.onTheFly = onTheFly;
    }

    protected void registerMethodCallError(PsiMethodCallExpression expression){
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiElement nameToken = methodExpression.getReferenceNameElement();
        registerError(nameToken);
    }

    protected void registerStatementError(PsiStatement statement){
        final PsiElement statementToken = statement.getFirstChild();
        registerError(statementToken);
    }

    protected void registerClassError(PsiClass aClass){
        final PsiElement nameIdentifier = aClass.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerMethodError(PsiMethod method){
        final PsiElement nameIdentifier = method.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerVariableError(PsiVariable variable){
        final PsiElement nameIdentifier = variable.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerTypeParameterError(PsiTypeParameter param){
        final PsiElement nameIdentifier = param.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerFieldError(PsiField field){
        final PsiElement nameIdentifier = field.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerModifierError(String modifier,
                                         PsiModifierListOwner parameter){
        final PsiModifierList modifiers = parameter.getModifierList();
        if(modifiers == null){
            return;
        }
        final PsiElement[] children = modifiers.getChildren();
        for(final PsiElement child : children){
            final String text = child.getText();
            if(modifier.equals(text)){
                registerError(child);
            }
        }
    }

    protected void registerError(PsiElement location){
        if(location == null){
            return;
        }
        final LocalQuickFix[] fix = createFixes(location);
        final String description = inspection.buildErrorString(location);
        registerError(location, description, fix);
    }

    private void registerError(PsiElement location, String description,
                               LocalQuickFix[] fixes){
        final ProblemDescriptor problem =
                inspectionManager.createProblemDescriptor(
                        location, description, fixes,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addError(problem);
    }

    private void addError(ProblemDescriptor problem){
        if(errors == null){
            errors = new ArrayList<ProblemDescriptor>(5);
        }
        errors.add(problem);
    }

    protected void registerError(PsiElement location, Object arg){
        final LocalQuickFix[] fix = createFixes(location);
        final String description = inspection.buildErrorString(arg);
        registerError(location, description, fix);
    }

    @Nullable
    private LocalQuickFix[] createFixes(PsiElement location){
        if(!onTheFly &&
                inspection.buildQuickFixesOnlyForOnTheFlyErrors()){
            return null;
        }
        final InspectionGadgetsFix[] fixes = inspection.buildFixes(location);
        if(fixes != null){
            for (InspectionGadgetsFix fix : fixes){
                location.putCopyableUserData(InspectionGadgetsFix.FIX_KEY,
                                             fix.getName());
            }
            return fixes;
        }
        final InspectionGadgetsFix fix = inspection.buildFix(location);
        if(fix == null){
            return null;
        }
        location.putCopyableUserData(InspectionGadgetsFix.FIX_KEY,
                                     fix.getName());
        return new InspectionGadgetsFix[]{fix};
    }

    @Nullable
    public ProblemDescriptor[] getErrors(){
        if(errors == null){
            return null;
        } else{
            final int numErrors = errors.size();
            return errors.toArray(new ProblemDescriptor[numErrors]);
        }
    }

    public void visitWhiteSpace(PsiWhiteSpace space){
        // none of our inspections need to do anything with white space,
        // so this is a performance optimization
    }

    public void visitClass(PsiClass aClass) {
        // only visit a class if we're starting in it, to prevent duplicate
        // messages.
        if(inspection instanceof ClassInspection && !classVisited) {
            classVisited = true;
            super.visitClass(aClass);
        } else if (inspection instanceof ExpressionInspection) {
            super.visitClass(aClass);
        } else if (inspection instanceof FileInspection) {
            super.visitClass(aClass);
        }
    }
}