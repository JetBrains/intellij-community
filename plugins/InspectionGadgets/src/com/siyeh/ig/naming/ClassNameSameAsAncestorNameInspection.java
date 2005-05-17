package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ClassNameSameAsAncestorNameInspection extends ClassInspection{
    private final RenameFix fix = new RenameFix();

    public String getDisplayName(){
        return "Class name same as ancestor name";
    }

    public String getGroupDisplayName(){
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Class name '#ref' is the same as one of its superclass' names #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ClassNameSameAsAncestorNameVisitor();
    }

    private static class ClassNameSameAsAncestorNameVisitor
            extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down into inner classes
            final String className = aClass.getName();
            if(className == null){
                return;
            }
            final Set<PsiClass> alreadyVisited = new HashSet<PsiClass>(8);
            final PsiClass[] supers = aClass.getSupers();
            for(final PsiClass aSuper : supers){
                if(hasMatchingName(aSuper, className, alreadyVisited)){
                    registerClassError(aClass);
                }
            }
        }

        private static boolean hasMatchingName(PsiClass aSuper,
                                               String className,
                                               Set<PsiClass> alreadyVisited){
            if(aSuper == null){
                return false;
            }
            if(alreadyVisited.contains(aSuper)){
                return false;
            }
            alreadyVisited.add(aSuper);
            final String superName = aSuper.getName();
            if(className.equals(superName)){
                return true;
            }
            final PsiClass[] supers = aSuper.getSupers();
            for(PsiClass aSupers : supers){
                if(hasMatchingName(aSupers, className, alreadyVisited)){
                    return true;
                }
            }
            return false;
        }
    }
}
