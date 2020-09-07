/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.java.validation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.IntelliLangBundle;
import org.intellij.plugins.intelliLang.util.AbstractAnnotationNotApplicableInspection;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;

public class InjectionNotApplicable extends AbstractAnnotationNotApplicableInspection {

  @Override
  protected String getAnnotationName(Project project) {
    return Configuration.getProjectInstance(project).getAdvancedConfiguration().getLanguageAnnotationClass();
  }

  @Override
  protected boolean isTypeApplicable(PsiType type) {
    return type == null || !PsiUtilEx.isStringOrStringArray(type);
  }

  @Override
  protected String getDescriptionTemplate() {
    return IntelliLangBundle.message("inspection.message.language.injection.only.applicable.to.elements.type.string");
  }

}