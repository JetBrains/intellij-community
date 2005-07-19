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

public class UtilityClassUtil {
    private UtilityClassUtil() {
        super();
    }

    public static boolean isUtilityClass(@NotNull PsiClass aClass) {
        if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
            return false;
        }
        if(aClass instanceof PsiTypeParameter ||
                aClass instanceof PsiAnonymousClass){
            return false;
        }
        final PsiReferenceList extendsList = aClass.getExtendsList();
        if (extendsList != null
                && extendsList.getReferenceElements().length > 0) {
            return false;
        }
        final PsiReferenceList implementsList = aClass.getImplementsList();
        if (implementsList != null
                && implementsList.getReferenceElements().length > 0) {
            return false;
        }
        if (!allMethodsStatic(aClass)) {
            return false;
        }
        if (!allFieldsStatic(aClass)) {
            return false;
        }
        return !(aClass.getMethods().length == 0 &&
                aClass.getFields().length == 0);
    }

    private static boolean allFieldsStatic(@NotNull PsiClass aClass) {
        boolean allFieldsStatic = true;
        final PsiField[] fields = aClass.getFields();
        for(final PsiField field : fields){
            if(!field.hasModifierProperty(PsiModifier.STATIC)){
                allFieldsStatic = false;
            }
        }
        return allFieldsStatic;
    }

    private static boolean allMethodsStatic(@NotNull PsiClass aClass) {
        final PsiMethod[] methods = aClass.getMethods();
        for(final PsiMethod method : methods){
            if(!(method.isConstructor() ||
                    method.hasModifierProperty(PsiModifier.STATIC))){
                return false;
            }
        }
        return true;
    }
}
