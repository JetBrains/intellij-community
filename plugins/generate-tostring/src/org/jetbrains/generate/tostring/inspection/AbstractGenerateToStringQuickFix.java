/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Base class to extend for different types of quick fixes.
 */
public abstract class AbstractGenerateToStringQuickFix implements LocalQuickFix {

    protected PsiAdapter psi;

    public AbstractGenerateToStringQuickFix() {
        psi = PsiAdapterFactory.getPsiAdapter();
    }

    @NotNull
    public String getName() {
        return "Generate toString()";
    }

    @NotNull
    public String getFamilyName() {
        return "Generate";
    }

}
