package com.siyeh.ig.internationalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class StringTokenizerInspection extends VariableInspection {
    public String getID(){
        return "UseOfStringTokenizer";
    }
    public String getDisplayName() {
        return "Use of StringTokenizer";
    }

    public String getGroupDisplayName() {
        return GroupNames.INTERNATIONALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref in an internationalized context #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringTokenizerVisitor();
    }

    private static class StringTokenizerVisitor extends BaseInspectionVisitor {


        public void visitVariable(@NotNull PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (!TypeUtils.typeEquals("java.util.StringTokenizer", deepComponentType)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

    }

}
