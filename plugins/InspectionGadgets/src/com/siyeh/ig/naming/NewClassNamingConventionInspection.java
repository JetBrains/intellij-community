/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/*
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.NamingConvention;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NewClassNamingConventionInspection extends AbstractNamingConventionInspection<PsiClass> {
  public static final ExtensionPointName<NamingConvention<PsiClass>> EP_NAME = ExtensionPointName.create("com.intellij.naming.convention.class");

  public NewClassNamingConventionInspection() {
    super(EP_NAME.getExtensions(), ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.naming.convention.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof PsiClass) {
          PsiClass aClass = (PsiClass)element;
          final String name = aClass.getName();
          if (name == null) return;
          checkName(aClass, name, shortName -> registerClassError(aClass, name, shortName));
        }
      }
    };
  }
}