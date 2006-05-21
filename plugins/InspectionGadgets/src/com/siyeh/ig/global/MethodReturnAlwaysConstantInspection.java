package com.siyeh.ig.global;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.psiutils.MethodInheritanceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MethodReturnAlwaysConstantInspection extends BaseGlobalInspection {
    private static final Key<Boolean> ALWAYS_CONSTANT = Key.create("ALWAYS_CONSTANT");

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext) {
        if (!(refEntity instanceof RefMethod)) {
            return null;
        }
        if (globalContext.isSuppressed(refEntity, getShortName())) {
            return null;
        }
        final RefMethod refMethod = (RefMethod) refEntity;
        final Boolean alreadyProcessed = refMethod.getUserData(ALWAYS_CONSTANT);
        if (alreadyProcessed != null && alreadyProcessed) {
            return null;
        }
        if (!(refMethod.getElement()instanceof PsiMethod)) {
            return null;
        }
        final PsiMethod method = (PsiMethod) refMethod.getElement();
        if (method.getBody() == null) {
            return null;     //we'll catch it on another method
        }
        if (!alwaysReturnsConstant(method)) {
            return null;
        }
        final Set<RefMethod> siblingMethods = MethodInheritanceUtils.calculateSiblingMethods(refMethod);
        for (RefMethod siblingMethod : siblingMethods) {
            final PsiMethod siblingPsiMethod = (PsiMethod) siblingMethod.getElement();
            if (method.getBody() != null && !alwaysReturnsConstant(siblingPsiMethod)) {
                return null;
            }
        }
        final List<ProblemDescriptor> out = new ArrayList<ProblemDescriptor>();
        for (RefMethod siblingMethod : siblingMethods) {
            if (!globalContext.isSuppressed(siblingMethod, getShortName())) {
                final PsiMethod siblingPsiMethod = (PsiMethod) siblingMethod.getElement();
                out.add(manager.createProblemDescriptor(siblingPsiMethod, InspectionsBundle.message(
                        "method.return.always.constant.problem.descriptor"), (LocalQuickFix[]) null,
                                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
            siblingMethod.putUserData(ALWAYS_CONSTANT, true);
        }
        return out.toArray(new ProblemDescriptor[out.size()]);
    }

    private boolean alwaysReturnsConstant(PsiMethod method) {
        final PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        final PsiStatement[] statements = body.getStatements();
        if (statements.length != 1) {
            return false;
        }
        final PsiStatement statement = statements[0];
        if (!(statement instanceof PsiReturnStatement)) {
            return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement) statement;
        final PsiExpression value = returnStatement.getReturnValue();
        return value != null && PsiUtil.isConstantExpression(value);
    }
}
