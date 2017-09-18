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

import com.intellij.psi.PsiMember;
import com.siyeh.InspectionGadgetsBundle;

public abstract class NamingConvention<T extends PsiMember>  {
  
  public abstract boolean isApplicable(T member);
  public abstract String getElementDescription();
  public abstract String getShortName();
  public abstract NamingConventionBean createDefaultBean();


  public String createErrorMessage(String name, NamingConventionBean bean) {
    final int length = name.length();
    if (length < bean.m_minLength) {
      return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.short", getElementDescription(),
                                             Integer.valueOf(length), Integer.valueOf(bean.m_minLength));
    }
    else if (bean.m_maxLength > 0 && length > bean.m_maxLength) {
      return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.long", getElementDescription(),
                                             Integer.valueOf(length), Integer.valueOf(bean.m_maxLength));
    }
    return InspectionGadgetsBundle.message("naming.convention.problem.descriptor.regex.mismatch", getElementDescription(), bean.m_regex);
  }

  
  
}
