/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jdom.Element;

/**
 * @author Gregory.Shrago
 */
public abstract class AbstractLanguageInjectionSupport extends LanguageInjectionSupport {

  public boolean useDefaultInjector(final PsiElement host) {
    return false;
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
    return new BaseInjection(getId());
  }

  public void setupPresentation(final BaseInjection injection, final SimpleColoredText presentation, final boolean isSelected) {
    presentation.append(injection.getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
  }

  public Configurable[] createSettings(final Project project, final Configuration configuration) {
    return new Configurable[0];
  }

  public AnAction[] createAddActions(final Project project, final Consumer<BaseInjection> consumer) {
    return AnAction.EMPTY_ARRAY;
  }

  public AnAction createEditAction(final Project project, final Factory<BaseInjection> producer) {
    return null;
  }
}
