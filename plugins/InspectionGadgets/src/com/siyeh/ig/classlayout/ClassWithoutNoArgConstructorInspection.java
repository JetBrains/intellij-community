package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ClassWithoutNoArgConstructorInspection extends ClassInspection{
    /** @noinspection PublicField*/
    public boolean m_ignoreClassesWithNoConstructors = true;


    public String getDisplayName(){
        return "Class without no-arg constructor";
    }

    public String getGroupDisplayName(){
        return GroupNames.JAVABEANS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore if class has default constructor",
                                              this,
                                              "m_ignoreClassesWithNoConstructors");
    }

    public String buildErrorString(PsiElement location){
        return "#ref has no no-arg constructor #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ClassWithoutNoArgConstructorVisitor();
    }

    private class ClassWithoutNoArgConstructorVisitor
            extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            if(aClass.isInterface() || aClass.isEnum() ||
                    aClass.isAnnotationType()){
                return;
            }
            if(aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass){
                return;
            }
            if(m_ignoreClassesWithNoConstructors &&
                    !classHasConstructor(aClass)){
                return;
            }
            if(classHasNoArgConstructor(aClass)){
                return;
            }
            registerClassError(aClass);
        }
    }

    private static boolean classHasNoArgConstructor(PsiClass aClass){
        final PsiMethod[] constructors = aClass.getConstructors();
        for(final PsiMethod constructor : constructors){
            final PsiParameterList parameterList =
                    constructor.getParameterList();
            if(parameterList != null){
                final PsiParameter[] parameters = parameterList.getParameters();
                if(parameters != null && parameters.length == 0){
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean classHasConstructor(PsiClass aClass){
        final PsiMethod[] constructors = aClass.getConstructors();
        return constructors.length != 0;
    }
}
