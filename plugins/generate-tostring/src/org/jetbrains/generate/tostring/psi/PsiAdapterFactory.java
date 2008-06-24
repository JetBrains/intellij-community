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
package org.jetbrains.generate.tostring.psi;

import org.jetbrains.generate.tostring.psi.idea7.PsiAdapter7;

/**
 * Factory to get a PsiAdapter class compatible with the correct version of IDEA.
 *
 * @see PsiAdapter
 */
public class PsiAdapterFactory {

    private static PsiAdapter instance; // singleton instance

    /**
     * Gets the PsiAdapter
     *
     * @return the PsiAdapter used for the current version of IDEA.
     */
    public static PsiAdapter getPsiAdapter() {
        if (instance == null) {
            instance = new PsiAdapter7();
        }

        return instance;
    }

}
