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

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class PropertyResolver extends PropertyProviderFinder {
  private final String myPropertyName;
  private PropertiesProvider myResult;
  private Set<String> myVariants = new HashSet<>();

  private PropertyResolver(@NotNull String propertyName, DomElement contextElement) {
    super(contextElement);
    myPropertyName = propertyName;
  }

  public void visitAntDomAntCallParam(AntDomAntCallParam antCallParam) {
    // deliberately skip ancall params, they will be processed as a special case
  }

  @NotNull
  public static Trinity<PsiElement, Collection<String>, PropertiesProvider> resolve(@NotNull AntDomProject project, @NotNull String propertyName, DomElement contextElement) {
    final PropertyResolver resolver = new PropertyResolver(propertyName, contextElement);
    resolver.execute(project, project.getDefaultTarget().getRawText());
    if (resolver.getContextElement() instanceof PropertiesProvider) {
      // special case - when context element is a property provider itself
      resolver.propertyProviderFound((PropertiesProvider)resolver.getContextElement());
    }
    return resolver.getResult();

  }

  @NotNull
  public Trinity<PsiElement, Collection<String>, PropertiesProvider> getResult() {
    final PsiElement element = myResult != null ? myResult.getNavigationElement(myPropertyName) : null;
    return new Trinity<>(element, Collections.unmodifiableSet(myVariants), myResult);
  }

  @Override
  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
    boolean found = false;
    for (Iterator<String> it = propertiesProvider.getNamesIterator(); it.hasNext();) {
      final String providerProperty = it.next();
      myVariants.add(providerProperty);
      if (myPropertyName.equals(providerProperty)) {
        found = true;
      }
    }
    if (found) {
      myResult = propertiesProvider;
      stop();
    }
  }
}
