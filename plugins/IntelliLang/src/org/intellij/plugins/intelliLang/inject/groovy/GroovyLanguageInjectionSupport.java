/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject.groovy;

import com.intellij.lang.Language;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

/**
 * @author Gregory.Shrago
 */
public class GroovyLanguageInjectionSupport implements LanguageInjectionSupport {
  @NonNls private static final String SUPPORT_ID = "groovy";

  @NotNull
  public String getId() {
    return SUPPORT_ID;
  }

  @NotNull
  public Class[] getPatternClasses() {
    return new Class[] {GroovyPatterns.class};
  }

  public boolean addInjectionInPlace(final Language language, final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public boolean removeInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public boolean editInjectionInPlace(final PsiLanguageInjectionHost psiElement) {
    return false;
  }

  public BaseInjection createInjection(final Element element) {
    return new BaseInjection(SUPPORT_ID);
  }

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[0];
  }
}
