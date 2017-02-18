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

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class AntStringResolver extends PropertyProviderFinder{
  private final PropertyExpander myExpander;
  private final boolean mySkipCustomTags;
  private static final Key<Map<String, String>> RESOLVED_STRINGS_MAP_KEY = Key.create("_ant_resolved_strings_cache_");

  private AntStringResolver(DomElement contextElement, PropertyExpander expander) {
    super(contextElement);
    myExpander = expander;
    mySkipCustomTags = CustomAntElementsRegistry.ourIsBuildingClasspathForCustomTagLoading.get();
  }


  public void visitAntDomCustomElement(AntDomCustomElement custom) {
    if (!mySkipCustomTags) {
      super.visitAntDomCustomElement(custom);
    }
  }

  @NotNull
  public static String computeString(@NotNull final DomElement context, @NotNull String valueString) {
    PropertyExpander expander = new PropertyExpander(valueString);
    if (!expander.hasPropertiesToExpand()) {
      return valueString;
    }
    
    final Map<String, String> cached = RESOLVED_STRINGS_MAP_KEY.get(context);
    if (cached != null) {
      expander.acceptProvider(new CachedPropertiesProvider(cached));
      if (!expander.hasPropertiesToExpand()) {
        return expander.getResult();
      }
    }
    
    expander.setPropertyExpansionListener(new PropertyExpander.PropertyExpansionListener() {
      public void onPropertyExpanded(String propName, String propValue) {
        cacheResult(context, RESOLVED_STRINGS_MAP_KEY, propName, propValue);
      }
    });
    
    AntDomProject project = context.getParentOfType(AntDomProject.class, false);
    if (project == null) {
      return expander.getResult();
    }
    project = project.getContextAntProject();

    new AntStringResolver(context, expander).execute(project, project.getDefaultTarget().getRawText());

    return expander.getResult();
  }

  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
    myExpander.acceptProvider(propertiesProvider);
    if (!myExpander.hasPropertiesToExpand()) {
      stop();
    }
  }

  private static class CachedPropertiesProvider implements PropertiesProvider, PropertiesProvider.SkipPropertyExpansionInValues {
    Set<String> allNames;
    private final Map<String, String> myCached;

    public CachedPropertiesProvider(Map<String, String> cached) {
      myCached = cached;
    }

    @NotNull
    public Iterator<String> getNamesIterator() {
      if (allNames == null) {
        allNames = new HashSet<>(myCached.keySet());
      }
      return allNames.iterator();
    }

    public String getPropertyValue(String propertyName) {
      return myCached.get(propertyName);
    }

    public PsiElement getNavigationElement(String propertyName) {
      return null;
    }
  }
}

