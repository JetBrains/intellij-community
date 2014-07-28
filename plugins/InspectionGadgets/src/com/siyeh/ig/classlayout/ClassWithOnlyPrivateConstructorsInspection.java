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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeClassFinalFix;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ClassWithOnlyPrivateConstructorsInspection extends ClassWithOnlyPrivateConstructorsInspectionBase {

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new MakeClassFinalFix((PsiClass)infos[0]);
  }
}
