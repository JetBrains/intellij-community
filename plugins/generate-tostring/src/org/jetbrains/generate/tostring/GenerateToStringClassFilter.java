/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.generate.tostring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Contract;

/**
 * An extension which allows to prohibit toString generation for some classes
 */
public interface GenerateToStringClassFilter {
    ExtensionPointName<GenerateToStringClassFilter> EP_NAME = ExtensionPointName.create("com.intellij.generation.toStringClassFilter");

    /**
     * @param psiClass class to check
     * @return return false if toString should not be generated for the class
     */
    @Contract(pure = true)
    boolean canGenerateToString(PsiClass psiClass);
}
