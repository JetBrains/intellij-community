/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;

/**
 * @author Bas Leijdekkers
 */
public class DelegatingFixFactory {

  public static InspectionGadgetsFix createMakeSerializableFix(PsiClass aClass) {
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_IO_SERIALIZABLE, aClass);
    return new DelegatingFix(QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true));
  }

  public static InspectionGadgetsFix createMakeCloneableFix(PsiClass aClass) {
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_CLONEABLE, aClass);
    return new DelegatingFix(QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true));
  }
}