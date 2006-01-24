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

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class SingletonUtil {
    private SingletonUtil() {
        super();
    }

    public static boolean isSingleton(@NotNull PsiClass aClass) {
        if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
            return false;
        }
        if(aClass instanceof PsiTypeParameter ||
                aClass instanceof PsiAnonymousClass){
            return false;
        }                         
        if (!hasConstructor(aClass)) {
            return false;
        }
        if (hasVisibleConstructor(aClass)) {
            return false;
        }
        return containsOneStaticSelfInstance(aClass);
    }

    private static boolean containsOneStaticSelfInstance(PsiClass aClass) {
        final PsiField[] fields = aClass.getFields();
        int numSelfInstances = 0;
        for(final PsiField field : fields){
            final String className = aClass.getQualifiedName();
            if(field.hasModifierProperty(PsiModifier.STATIC)){
                final PsiType type = field.getType();
                final String fieldTypeName = type.getCanonicalText();
                if(fieldTypeName.equals(className)){
                    numSelfInstances++;
                }
            }
        }
        return numSelfInstances == 1;
    }

    private static boolean hasConstructor(PsiClass aClass) {
        return aClass.getConstructors().length>0;
    }

    private static boolean hasVisibleConstructor(PsiClass aClass) {
        final PsiMethod[] methods = aClass.getConstructors();
        for(final PsiMethod method : methods){
            if(method.hasModifierProperty(PsiModifier.PUBLIC)){
                return true;
            }
            if(!method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !method.hasModifierProperty(PsiModifier.PROTECTED)){
                return true;
            }
        }
        return false;
    }
}
