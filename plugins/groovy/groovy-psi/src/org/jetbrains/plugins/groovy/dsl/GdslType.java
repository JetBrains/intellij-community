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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWildcardType;

/**
 * @author peter
 */
public class GdslType {
  public final PsiType psiType;

  public GdslType(PsiType psiType) {
    this.psiType = psiType;
  }

  public String getShortName() {
    return StringUtil.getShortName(getName());
  }

  public String getName() {
    PsiType type = psiType;
    if (type instanceof PsiWildcardType) {
      type = ((PsiWildcardType)type).getBound();
    }
    if (type instanceof PsiClassType) {
      final PsiClass resolve = ((PsiClassType)type).resolve();
      if (resolve != null) {
        return resolve.getName();
      }
      final String canonicalText = type.getCanonicalText();
      final int i = canonicalText.indexOf('<');
      if (i < 0) return canonicalText;
      return canonicalText.substring(0, i);
    }

    if (type == null) {
      return "";
    }

    return type.getCanonicalText();
  }

}
