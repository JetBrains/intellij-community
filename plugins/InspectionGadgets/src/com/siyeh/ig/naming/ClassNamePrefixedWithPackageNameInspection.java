package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

import java.util.StringTokenizer;

public class ClassNamePrefixedWithPackageNameInspection extends ClassInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Class name prefixed with package name";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Class name '#ref' begins with its package name #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassNameBePrefixedWithPackageNameVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassNameBePrefixedWithPackageNameVisitor extends BaseInspectionVisitor {
        private ClassNameBePrefixedWithPackageNameVisitor(BaseInspection inspection,
                                                          InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down into inner classes
            final String className = aClass.getName();
            final String qualifiedName = aClass.getQualifiedName();
            if (className == null) {
                return;
            }
            if (qualifiedName == null) {
                return;
            }
            if (className.equals(qualifiedName)) {
                return;
            }
            final StringTokenizer tokenizer = new StringTokenizer(qualifiedName, ".");
            String currentPackageName = null;
            String lastPackageName = null;
            while (tokenizer.hasMoreTokens()) {
                lastPackageName = currentPackageName;
                currentPackageName = tokenizer.nextToken();
            }

            if (lastPackageName == null) {
                return;
            }
            final String lowercaseClassName = className.toLowerCase();
            final String lowercasePackageName = lastPackageName.toLowerCase();
            if (!lowercaseClassName.startsWith(lowercasePackageName)) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
