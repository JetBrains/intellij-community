/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;

/**
 * User: anna
 * Date: 10/7/11
 */
public class InspectionsPropertiesReferenceProviderContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    ElementPattern pattern = XmlPatterns.xmlAttribute().withParent(StandardPatterns.or(XmlPatterns.xmlTag().withName("localInspection"), XmlPatterns.xmlTag().withName("globalInspection")));
    registrar.registerReferenceProvider(pattern, new InspectionsKeyPropertiesReferenceProvider(false), PsiReferenceRegistrar.DEFAULT_PRIORITY);
  }
}