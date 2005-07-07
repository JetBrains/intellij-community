package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class AbstractClassExtendsConcreteClassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Abstract class extends concrete class";
    }

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class #ref is declared 'abstract', and extends a concrete class #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AbstractClassExtendsConcreteClassVisitor();
    }

    private static class AbstractClassExtendsConcreteClassVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (aClass.isInterface()|| aClass.isAnnotationType()) {
                return;
            }
            if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final PsiClass superClass = aClass.getSuperClass();
            if (superClass == null) {
                return;
            }
            if (superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            final String superclassName = superClass.getQualifiedName();
            if ("java.lang.Object".equals(superclassName)) {
                return;
            }
            registerClassError(aClass);
        }
    }

}
