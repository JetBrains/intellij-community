package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class CyclomaticComplexityInspection
        extends MethodMetricInspection {
    public String getID(){
        return "OverlyComplexMethod";
    }
    public String getDisplayName() {
        return "Overly complex method";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    protected int getDefaultLimit() {
        return 10;
    }

    protected String getConfigurationLabel() {
        return "Method complexity limit:";
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
        method.accept(visitor);
        final int coupling = visitor.getComplexity();
        return "#ref is overly complex (cyclomatic complexity = " + coupling + ") #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodComplexityVisitor();
    }

    private class MethodComplexityVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
            method.accept(visitor);
            final int complexity = visitor.getComplexity();

            if (complexity <= getLimit()) {
                return;
            }
            registerMethodError(method);
        }
    }

}
