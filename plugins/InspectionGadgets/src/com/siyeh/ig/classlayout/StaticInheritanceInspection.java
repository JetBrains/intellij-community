package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class StaticInheritanceInspection extends ClassInspection {

    public String getDisplayName() {
        return "Static inheritance";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Interface #ref is implemented only for it's static constants #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StaticInheritanceVisitor(this, inspectionManager, onTheFly);
    }

    private static class StaticInheritanceVisitor extends BaseInspectionVisitor {
        private StaticInheritanceVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] refs = implementsList.getReferenceElements();
            for (int i = 0; i < refs.length; i++) {
                final PsiJavaCodeReferenceElement ref = refs[i];
                final PsiClass iface = (PsiClass) ref.resolve();
                if (iface != null) {
                    if (interfaceContainsOnlyConstants(iface)) {
                        registerError(ref);
                    }
                }
            }
        }

        private boolean interfaceContainsOnlyConstants(PsiClass iface) {
            if (iface.getAllFields().length == 0) {
                // ignore it, it's either a true interface or just a marker
                return false;
            }
            if (iface.getMethods().length != 0) {
                return false;
            }
            final PsiClass[] parentInterfaces = iface.getInterfaces();
            for (int i = 0; i < parentInterfaces.length; i++) {
                final PsiClass parentInterface = parentInterfaces[i];
                if (!interfaceContainsOnlyConstants(parentInterface)) {
                    return false;
                }
            }
            return true;
        }
    }

}
