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
package com.siyeh.ipp.exceptions;

import com.intellij.psi.PsiType;

import java.util.Comparator;

class HeirarchicalTypeComparator implements Comparator<PsiType>{
    
    public int compare(PsiType type1, PsiType type2){
        if(type1.isAssignableFrom(type2)){
            return 1;
        }
        if(type2.isAssignableFrom(type1)){
            return -1;
        }
        final String canonicalText1 = type1.getCanonicalText();
        return canonicalText1.compareTo(type2.getCanonicalText());
    }
}