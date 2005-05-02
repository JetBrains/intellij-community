package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class ClassInitializerInspection extends ClassInspection {

    public String getID(){
        return "NonStaticInitializer";
    }

    public String getDisplayName() {
        return "Non-static initializer";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-static initializer #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassInitializerVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassInitializerVisitor extends BaseInspectionVisitor {
        private ClassInitializerVisitor(BaseInspection inspection,
                                        InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for(final PsiClassInitializer initializer : initializers){
                if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    final PsiJavaToken leftBrace = body.getLBrace();
                    registerError(leftBrace);
                }
            }
        }
    }

}
