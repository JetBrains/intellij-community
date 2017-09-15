/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

public class EnumeratedClassNamingConvention extends NamingConvention<PsiClass> {

  private static final int DEFAULT_MIN_LENGTH = 8;
  private static final int DEFAULT_MAX_LENGTH = 64;

  @Override
  public NamingConventionBean createDefaultBean() {
    return new NamingConventionBean("[A-Z][A-Za-z\\d]*", DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
  }

  @Override
  public boolean isApplicable(PsiClass member) {
    return member.isEnum();
  }

  @Override
  public String getShortName() {
    return "EnumeratedClassNamingConvention";
  }

  @Override
  public String getElementDescription() {
    return InspectionGadgetsBundle.message("enumerated.class.naming.convention.element.description");
  }
}