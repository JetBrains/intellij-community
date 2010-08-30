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
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class AntStringResolver extends PropertyProviderFinder{
  private final PropertyExpander myExpander;
  private final boolean mySkipCustomTags;

  private AntStringResolver(DomElement contextElement, PropertyExpander expander) {
    super(contextElement);
    myExpander = expander;
    mySkipCustomTags = CustomAntElementsRegistry.ourIsBuildingClasspathForCustomTagLoading.get();
  }

  private static final Key<Map<String, String>> RESOLVED_STRINGS_MAP_KEY = new Key<Map<String, String>>("_ant_resolved_strings_cache_");

  public void visitAntDomCustomElement(AntDomCustomElement custom) {
    if (!mySkipCustomTags) {
      super.visitAntDomCustomElement(custom);
    }
  }

  @NotNull
  public static String computeString(@NotNull DomElement context, @NotNull String valueString) {
    PropertyExpander expander = new PropertyExpander(valueString);
    if (!expander.hasPropertiesToExpand()) {
      return valueString;
    }
    AntDomProject project = context.getParentOfType(AntDomProject.class, false);
    if (project == null) {
      return valueString;
    }
    project = project.getContextAntProject();
    Map<String, String> cached = context.getUserData(RESOLVED_STRINGS_MAP_KEY);
    if (cached != null) {
      final String resolvedFromCache = cached.get(valueString);
      if (resolvedFromCache != null) {
        return resolvedFromCache;
      }
    }
    else {
      cached = Collections.synchronizedMap(new HashMap<String, String>());
      context.putUserData(RESOLVED_STRINGS_MAP_KEY, cached);
    }
    new AntStringResolver(context, expander).execute(project, project.getDefaultTarget().getRawText());
    final String result = expander.getResult();
    cached.put(valueString, result);
    return result;
  }


  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
    myExpander.acceptProvider(propertiesProvider);
    if (!myExpander.hasPropertiesToExpand()) {
      stop();
    }
  }


}

