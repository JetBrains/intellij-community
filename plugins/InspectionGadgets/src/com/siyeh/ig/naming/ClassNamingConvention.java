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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;

public class ClassNamingConvention extends NamingConvention<PsiClass> {
  public static final String CLASS_NAMING_CONVENTION_SHORT_NAME = "ClassNamingConvention";
  private static final int DEFAULT_MIN_LENGTH = 8;
  private static final int DEFAULT_MAX_LENGTH = 64;

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[A-Z][A-Za-z\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public boolean isApplicable(PsiClass member) {
    return true;
  }

  @Override
  public String getShortName() {
    return CLASS_NAMING_CONVENTION_SHORT_NAME;
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("class.naming.convention.element.description");
  }
}
