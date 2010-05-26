/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class PropertyResolver extends PropertyProviderFinder {
  private final String myPropertyName;
  private PropertiesProvider myResult;
  private Set<String> myVariants = new HashSet<String>();

  private PropertyResolver(@NotNull String propertyName, DomElement contextElement) {
    super(contextElement);
    myPropertyName = propertyName;
  }

  @NotNull
  public static Pair<PsiElement, Collection<String>> resolve(@NotNull AntDomProject project, @NotNull String propertyName, DomElement contextElement) {
    final PropertyResolver resolver = new PropertyResolver(propertyName, contextElement);
    resolver.execute(project, project.getDefaultTarget().getRawText());
    return resolver.getResult();

  }

  @NotNull
  public Pair<PsiElement, Collection<String>> getResult() {
    final PsiElement element = myResult != null ? myResult.getNavigationElement(myPropertyName) : null;
    return new Pair<PsiElement, Collection<String>>(element, Collections.unmodifiableSet(myVariants));
  }

  @Override
  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
    for (Iterator<String> it = propertiesProvider.getNamesIterator(); it.hasNext();) {
      myVariants.add(it.next());
    }
    if (propertiesProvider.getPropertyValue(myPropertyName) != null) {
      myResult = propertiesProvider;
      stop();
    }
  }
}
