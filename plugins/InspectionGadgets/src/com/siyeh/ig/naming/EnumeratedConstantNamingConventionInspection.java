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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiEnumConstant;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EnumeratedConstantNamingConventionInspection extends
                                                          ConventionInspection {

  private static final int DEFAULT_MIN_LENGTH = 5;
  private static final int DEFAULT_MAX_LENGTH = 32;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("enumerated.constant.naming.convention.display.name");
  }

  @Override
  protected String getElementDescription() {
    return InspectionGadgetsBundle.message("enumerated.constant.naming.convention.element.description");
  }

  @Override
  protected String getDefaultRegex() {
    return "[A-Z][A-Z_\\d]*";
  }

  @Override
  protected int getDefaultMinLength() {
    return DEFAULT_MIN_LENGTH;
  }

  @Override
  protected int getDefaultMaxLength() {
    return DEFAULT_MAX_LENGTH;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitEnumConstant(PsiEnumConstant constant) {
      super.visitEnumConstant(constant);
      final String name = constant.getName();
      if (name == null) {
        return;
      }
      if (isValid(name)) {
        return;
      }
      registerFieldError(constant, name);
    }
  }
}
