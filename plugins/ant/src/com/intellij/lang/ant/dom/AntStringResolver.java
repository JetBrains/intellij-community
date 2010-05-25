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

import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class AntStringResolver extends PropertyProviderFinder{
  private final PropertyExpander myExpander;

  private AntStringResolver(DomElement contextElement, PropertyExpander expander) {
    super(contextElement);
    myExpander = expander;
  }

  @NotNull
  public static String computeString(@NotNull DomElement context, @NotNull String valueString) {
    PropertyExpander expander = new PropertyExpander(valueString);
    if (!expander.hasPropertiesToExpand()) {
      return valueString;
    }
    final AntDomProject project = calcContextProject(context);
    if (project == null) {
      return valueString;
    }
    new AntStringResolver(context, expander).execute(project, project.getDefaultTarget().getRawText());
    return expander.getResult();
  }


  @Nullable
  private static AntDomProject calcContextProject(DomElement context) {
    // todo
    return context.getParentOfType(AntDomProject.class, false);
  }

  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
    myExpander.acceptProvider(propertiesProvider);
    if (!myExpander.hasPropertiesToExpand()) {
      stop();
    }
  }


}

