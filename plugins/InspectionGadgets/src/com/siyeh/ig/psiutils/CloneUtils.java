/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class CloneUtils{
    private CloneUtils(){
        super();
    }

    public static boolean isCloneable(@NotNull PsiClass aClass){
        return ClassUtils.isSubclass(aClass, "java.lang.Cloneable");
    }

    public static boolean isDirectlyCloneable(@NotNull PsiClass aClass){
        final PsiClass[] interfaces = aClass.getInterfaces();
        for(PsiClass anInterface : interfaces){
            if(anInterface != null){
                final String qualifiedName = anInterface.getQualifiedName();
                if("java.lang.Cloneable".equals(qualifiedName)){
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isClone(@NotNull PsiMethod method){
        final String methodName = method.getName();
        if(!HardcodedMethodConstants.CLONE.equals(methodName)) {
          return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if(parameters.length != 0){
            return false;
        }
        final PsiManager manager = method.getManager();
        final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
        if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
                languageLevel.equals(LanguageLevel.JDK_1_4)){
            //for 1.5 and after, clone may be covariant
            final PsiType returnType = method.getReturnType();
            if(!TypeUtils.isJavaLangObject(returnType)){
                return false;
            }
        }
        return true;
    }
}
