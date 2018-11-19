/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;

class Jdk5StringConcatenationPredicate extends SimpleStringConcatenationPredicate {

  Jdk5StringConcatenationPredicate() {
    super(true);
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) {
      return false;
    }
    return super.satisfiedBy(element);
  }
}
