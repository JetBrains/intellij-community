package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.VariableInspection;

import java.util.HashSet;
import java.util.Set;

public class ObsoleteCollectionInspection extends VariableInspection {

    private static final Set s_obsoleteCollectionTypes = new HashSet(2);

    static {
        s_obsoleteCollectionTypes.add("java.util.Vector");
        s_obsoleteCollectionTypes.add("java.util.Hashtable");
    }

    public String getDisplayName() {
        return "Use of obsolete collection type";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Obsolete collection type #ref used #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ObsoleteCollectionVisitor(this, inspectionManager, onTheFly);
    }

    private static class ObsoleteCollectionVisitor extends BaseInspectionVisitor {
        private ObsoleteCollectionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (deepComponentType == null) {
                return;
            }
            final String typeName = deepComponentType.getCanonicalText();
            if (!s_obsoleteCollectionTypes.contains(typeName)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitNewExpression(PsiNewExpression newExpression) {
            super.visitNewExpression(newExpression);
            final PsiType type = newExpression.getType();
            if (type == null) {
                return;
            }
            final String typeName = type.getCanonicalText();
            if (!s_obsoleteCollectionTypes.contains(typeName)) {
                return;
            }
            final PsiJavaCodeReferenceElement classNameElement = newExpression.getClassReference();
            registerError(classNameElement);
        }

    }

}
