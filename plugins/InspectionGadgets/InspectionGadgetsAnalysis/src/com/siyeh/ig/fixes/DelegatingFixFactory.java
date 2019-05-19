// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class DelegatingFixFactory {

  @Nullable
  public static InspectionGadgetsFix createMakeSerializableFix(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_IO_SERIALIZABLE, aClass);
    return new DelegatingFix(QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true));
  }

  @Nullable
  public static InspectionGadgetsFix createMakeCloneableFix(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return null;
    }
    final PsiClassType type = TypeUtils.getType(CommonClassNames.JAVA_LANG_CLONEABLE, aClass);
    return new DelegatingFix(QuickFixFactory.getInstance().createExtendsListFix(aClass, type, true));
  }
}