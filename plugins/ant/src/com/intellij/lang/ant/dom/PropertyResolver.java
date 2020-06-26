// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class PropertyResolver extends PropertyProviderFinder {
  private final String myPropertyName;
  private PropertiesProvider myResult;
  private final Set<String> myVariants = new HashSet<>();

  private PropertyResolver(@NotNull String propertyName, DomElement contextElement) {
    super(contextElement);
    myPropertyName = propertyName;
  }

  @Override
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
