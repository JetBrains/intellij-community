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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.inheritance.ImplicitSubclassProvider;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

public class DevKitImplicitSubclassProvider extends ImplicitSubclassProvider {
  @Override
  public boolean isApplicableTo(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiAnonymousClass) return false;

    return DevKitImplicitUsageProvider.isDomElementClass(psiClass);
  }

  @Override
  @Nullable
  public SubclassingInfo getSubclassingInfo(@NotNull PsiClass psiClass) {
    if (DevKitImplicitUsageProvider.isDomElementClass(psiClass)) {
      return new SubclassingInfo(DevKitBundle.message("implemented.at.runtime.dom"));
    }

    return null;
  }
}
