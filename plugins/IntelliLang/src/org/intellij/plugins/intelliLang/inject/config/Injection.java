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
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.intellij.plugins.intelliLang.Configuration;

import java.util.List;

/**
 * Represents an element for the UI-driven language-injection. Each element specifies
 * the following properties:
 * <ul>
 * <li>language-id
 * <li>prefix
 * <li>suffix
 * <li>injection range (based on value-pattern for XML-related injections)
 * <li>friendly name for displaying the entry
 * </ul>
 */
public interface Injection {

  @NotNull
  String getInjectedLanguageId();

  @NotNull
  String getPrefix();

  @NotNull
  String getSuffix();

  @NotNull
  List<TextRange> getInjectedArea(PsiElement element);

  /**
   * Determines how the injection would like being displayed (e.g. attributes
   * return a qualified TAG-NAME/@ATT-NAME combination name instead of just
   * the plain name.
   */
  @NotNull
  String getDisplayName();

  boolean acceptsPsiElement(final PsiElement element);
}
