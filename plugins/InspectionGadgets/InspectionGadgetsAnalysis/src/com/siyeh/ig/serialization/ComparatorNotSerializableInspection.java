/*
 * Copyright 2006-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DelegatingFixFactory;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComparatorNotSerializableInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("comparator.not.serializable.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("comparator.not.serializable.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return DelegatingFixFactory.createMakeSerializableFix((PsiClass)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ComparatorNotSerializableVisitor();
  }

  private static class ComparatorNotSerializableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      if (aClass instanceof PsiAnonymousClass || !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COMPARATOR) ||
          SerializationUtils.isSerializable(aClass)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}