package com.siyeh.ig;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.siyeh.ig.dependency.DependencyMap;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseInspectionVisitor extends PsiRecursiveElementVisitor {
    private final BaseInspection m_inspection;
    private final InspectionManager m_inspectionManager;
    protected final boolean m_onTheFly;
    private List m_errors = null;

    protected BaseInspectionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean onTheFly) {
        super();
        m_inspection = inspection;
        m_inspectionManager = inspectionManager;
        m_onTheFly = onTheFly;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = expression.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
    }

    protected void registerMethodCallError(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiElement nameToken = methodExpression.getReferenceNameElement();
        registerError(nameToken);
    }

    protected void registerStatementError(PsiStatement statement) {
        final PsiElement statementToken = statement.getFirstChild();
        registerError(statementToken);
    }

    protected void registerClassError(PsiClass aClass) {
        final PsiElement nameIdentifier = aClass.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerMethodError(PsiMethod method) {
        final PsiElement nameIdentifier = method.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerVariableError(PsiVariable variable) {
        final PsiElement nameIdentifier = variable.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerTypeParameterError(PsiTypeParameter param) {
        final PsiElement nameIdentifier = param.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerFieldError(PsiField field) {
        final PsiElement nameIdentifier = field.getNameIdentifier();
        registerError(nameIdentifier);
    }

    protected void registerModifierError(String modifier, PsiModifierListOwner parameter) {
        final PsiModifierList modifiers = parameter.getModifierList();
        if (modifiers == null) {
            return;
        }
        final PsiElement[] children = modifiers.getChildren();
        for (int i = 0; i < children.length; i++) {
            final PsiElement child = children[i];
            final String text = child.getText();
            if (modifier.equals(text)) {
                registerError(child);
            }
        }
    }

    protected void registerError(PsiElement location) {
        if (location == null) {
            return;
        }
        final LocalQuickFix fix;
        if (!m_onTheFly && m_inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
            fix = null;
        } else if (m_onTheFly && m_inspection.buildQuickFixesOnlyForBatchErrors()) {
            fix = null;
        } else {
            fix = m_inspection.buildFix(location);
        }
        final String description = m_inspection.buildErrorString(location);
        final ProblemDescriptor problem
                = m_inspectionManager.createProblemDescriptor(location, description, fix,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addError(problem);
    }

    private void addError(ProblemDescriptor problem) {
        if (m_errors == null) {
            m_errors = new ArrayList(5);
        }
        m_errors.add(problem);
    }

    protected void registerError(PsiElement location, Object arg) {
        final LocalQuickFix fix;
        if (!m_onTheFly && m_inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
            fix = null;
        } else if (m_onTheFly && m_inspection.buildQuickFixesOnlyForBatchErrors()) {
            fix = null;
        } else {
            fix = m_inspection.buildFix(location);
        }
        final String description = m_inspection.buildErrorString(arg);
        final ProblemDescriptor problem
                = m_inspectionManager.createProblemDescriptor(location, description, fix,
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addError(problem);
    }


    public ProblemDescriptor[] getErrors() {
        final List errors = m_errors;
        if (errors == null) {
            return null;
        } else {
            final int numErrors = errors.size();
            return (ProblemDescriptor[]) errors.toArray(new ProblemDescriptor[numErrors]);
        }
    }

    public DependencyMap fetchDependencyMap()
    {
        final DependencyMap dependencyMap = (DependencyMap) m_inspectionManager.getProject().getComponent(DependencyMap.class);
        if(!m_onTheFly)
        {
            dependencyMap.waitForCompletion();
        }
        return dependencyMap;
    }

}
