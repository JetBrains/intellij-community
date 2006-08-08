/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseInspectionVisitor extends PsiElementVisitor{

    private BaseInspection inspection = null;
    private boolean onTheFly = false;
    private List<ProblemDescriptor> errors = null;
    private boolean classVisited = false;
    private ProblemsHolder holder = null;

    public void setInspection(BaseInspection inspection){
        this.inspection = inspection;
    }

    public void setOnTheFly(boolean onTheFly){
        this.onTheFly = onTheFly;
    }

    protected void registerMethodCallError(PsiMethodCallExpression expression,
                                           Object... infos){
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiElement nameToken = methodExpression.getReferenceNameElement();
        if (nameToken == null) {
            registerError(expression, infos);
        } else {
            registerError(nameToken, infos);
        }
    }

    protected void registerStatementError(PsiStatement statement,
                                          Object... infos){
        final PsiElement statementToken = statement.getFirstChild();
        if (statementToken == null) {
            registerError(statement, infos);
        } else {
            registerError(statementToken, infos);
        }
    }

    protected void registerClassError(PsiClass aClass, Object... infos){
        final PsiElement nameIdentifier;
        if (aClass instanceof PsiEnumConstantInitializer) {
            final PsiEnumConstantInitializer enumConstantInitializer =
                    (PsiEnumConstantInitializer)aClass;
            final PsiEnumConstant enumConstant =
                    enumConstantInitializer.getEnumConstant();
            nameIdentifier = enumConstant.getNameIdentifier();
        } else if (aClass instanceof PsiAnonymousClass) {
            final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)aClass;
            nameIdentifier = anonymousClass.getBaseClassReference();
        } else {
            nameIdentifier = aClass.getNameIdentifier();
        }
        if (nameIdentifier == null) {
            registerError(aClass.getContainingFile(), infos);
        } else {
            registerError(nameIdentifier, infos);
        }
    }

    protected void registerMethodError(PsiMethod method, Object... infos){
        final PsiElement nameIdentifier = method.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(method.getContainingFile(), infos);
        } else {
            registerError(nameIdentifier, infos);
        }
    }

    protected void registerVariableError(PsiVariable variable, Object... infos){
        final PsiElement nameIdentifier = variable.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(variable, infos);
        } else {
            registerError(nameIdentifier, infos);
        }
    }

    protected void registerTypeParameterError(PsiTypeParameter typeParameter,
                                              Object... infos){
        final PsiElement nameIdentifier = typeParameter.getNameIdentifier();
        if (nameIdentifier == null) {
            registerError(typeParameter, infos);
        } else {
            registerError(nameIdentifier, infos);
        }
    }

    protected void registerFieldError(PsiField field, Object... infos){
        final PsiElement nameIdentifier = field.getNameIdentifier();
        registerError(nameIdentifier, infos);
    }

    protected void registerModifierError(@NotNull String modifier,
                                         @NotNull PsiModifierListOwner parameter,
                                         Object... infos){
        final PsiModifierList modifiers = parameter.getModifierList();
        if(modifiers == null){
            return;
        }
        final PsiElement[] children = modifiers.getChildren();
        for(final PsiElement child : children){
            final String text = child.getText();
            if(modifier.equals(text)){
                registerError(child, infos);
            }
        }
    }

    protected void registerError(@NotNull PsiElement location, Object... infos){
        final LocalQuickFix[] fixes = createFixes(location);
        final String description = inspection.buildErrorString(infos);
        holder.registerProblem(location, description, fixes);
    }

    @Nullable
    private LocalQuickFix[] createFixes(PsiElement location){
        if(!onTheFly && inspection.buildQuickFixesOnlyForOnTheFlyErrors()){
            return null;
        }
        final InspectionGadgetsFix[] fixes = inspection.buildFixes(location);
        if(fixes != null){
            return fixes;
        }
        final InspectionGadgetsFix fix = inspection.buildFix(location);
        if(fix == null){
            return null;
        }
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

    public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
    }

    public void visitWhiteSpace(PsiWhiteSpace space){
        // none of our inspections need to do anything with white space,
        // so this is a performance optimization
    }

    public void visitClass(PsiClass aClass) {
        // only visit a class if we're starting in it, to prevent duplicate
        // messages which would occur if the visitor descended into
        // nested and anonymous classes.
        if(inspection instanceof ClassInspection && !classVisited) {
            classVisited = true;
            super.visitClass(aClass);
        } else if (inspection instanceof ExpressionInspection) {
            super.visitClass(aClass);
        } else if (inspection instanceof FileInspection) {
            super.visitClass(aClass);
        }
    }

    public void setProblemsHolder(ProblemsHolder holder) {
        this.holder = holder;
    }
}