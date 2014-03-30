/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.ReplaceInheritanceWithDelegationFix;

public class ExtendsThreadInspection extends ExtendsThreadInspectionBase {

  /**
   * @see com.siyeh.ig.inheritance.ExtendsConcreteCollectionInspection#buildFix(java.lang.Object...)
   */
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    return new ReplaceInheritanceWithDelegationFix();
  }
}