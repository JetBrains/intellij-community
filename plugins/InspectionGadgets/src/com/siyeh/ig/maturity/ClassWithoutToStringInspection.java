package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public class ClassWithoutToStringInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class without 'toString()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref should probably implement .toString(), for debugging purposes";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutToStringVisitor();
    }

    private static class ClassWithoutToStringVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //don't call super, to prevent drilldown
            if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                final String methodName = method.getName();
                final PsiParameterList paramList = method.getParameterList();
                final PsiParameter[] parameters = paramList.getParameters();
                if("toString".equals(methodName) && parameters.length == 0){
                    return;
                }
            }
            registerClassError(aClass);
        }
    }

}
