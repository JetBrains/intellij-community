package com.siyeh.ig.threading;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ReplaceInheritanceWithDelegationFix;
import org.jetbrains.annotations.NotNull;

public class ExtendsThreadInspection extends ClassInspection{
    private final ReplaceInheritanceWithDelegationFix fix = new ReplaceInheritanceWithDelegationFix();

    public String getID(){
        return "ClassExplicitlyExtendsThread";
    }

    public String getDisplayName(){
        return "Class explicitly extends java.lang.Thread";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }
    public String buildErrorString(PsiElement location){
        return "Class '#ref' explicitly extends java.lang.Thread #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ExtendsThreadVisitor();
    }

    private static class ExtendsThreadVisitor extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            if(aClass.isInterface() || aClass.isAnnotationType()|| aClass.isEnum()){
                return;
            }
            final PsiClass superClass = aClass.getSuperClass();
            if(superClass == null)
            {
                return;
            }
            final String superclassName = superClass.getQualifiedName();
            if(!"java.lang.Thread".equals(superclassName))
            {
                return;
            }
            registerClassError(aClass);
        }
    }
}
