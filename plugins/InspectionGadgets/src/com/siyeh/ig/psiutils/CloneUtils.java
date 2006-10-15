/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
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
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(method);

        final PsiClassType javaLangObject;
        if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
        javaLangObject = PsiType.getJavaLangObject(
                method.getManager(), method.getResolveScope());
        } else {
            // for 1.5 and after, clone may be covaraiant
            javaLangObject = null;
        }
        return MethodUtils.methodMatches(method, null, javaLangObject,
                HardcodedMethodConstants.CLONE, PsiType.EMPTY_ARRAY);
    }

    public static boolean onlyThrowsCloneNotSupportedException(
            @NotNull PsiMethod method){
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return false;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements.length != 1){
            return false;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiThrowStatement)){
            return false;
        }
        final PsiThrowStatement throwStatement =
                (PsiThrowStatement)statement;
        final PsiExpression exception = throwStatement.getException();
        if(!(exception instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression = (PsiNewExpression)exception;
        final PsiJavaCodeReferenceElement classReference =
                newExpression.getClassReference();
        if(classReference == null){
            return false;
        }
        final String qualifiedName = classReference.getQualifiedName();
        return qualifiedName.equals("java.lang.CloneNotSupportedException");
    }
}
