package com.siyeh.ig;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseInspectionVisitor extends PsiRecursiveElementVisitor{
    private final BaseInspection m_inspection;
    private final InspectionManager m_inspectionManager;
    private final boolean m_onTheFly;
    private List<ProblemDescriptor> m_errors = null;

    protected BaseInspectionVisitor(BaseInspection inspection,
                                    InspectionManager inspectionManager,
                                    boolean onTheFly){
        super();
        m_inspection = inspection;
        m_inspectionManager = inspectionManager;
        m_onTheFly = onTheFly;
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
        final LocalQuickFix fix = createFix(location);
        final String description = m_inspection.buildErrorString(location);
        registerError(location, description, fix);
    }

    private void registerError(PsiElement location, String description,
                               LocalQuickFix fix){
        final ProblemDescriptor problem
                =
                m_inspectionManager.createProblemDescriptor(location,
                                                            description, fix,
                                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addError(problem);
    }

    private void addError(ProblemDescriptor problem){
        if(m_errors == null){
            m_errors = new ArrayList<ProblemDescriptor>(5);
        }
        m_errors.add(problem);
    }

    protected void registerError(PsiElement location, Object arg){
        final LocalQuickFix fix = createFix(location);
        final String description = m_inspection.buildErrorString(arg);
        registerError(location, description, fix);
    }

    private LocalQuickFix createFix(PsiElement location){
        final LocalQuickFix fix;
        if(!m_onTheFly &&
                   m_inspection.buildQuickFixesOnlyForOnTheFlyErrors()){
            fix = null;
        } else{
            fix = m_inspection.buildFix(location);
        }
        return fix;
    }

    public ProblemDescriptor[] getErrors()
    {
        final List<ProblemDescriptor> errors = m_errors;
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
}
