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
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

public class AntAllTasksContainerImpl extends AntTaskImpl implements AntAllTasksContainer {

  public AntAllTasksContainerImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    /*
    if (myDefinition != null && definition != null && myDefinition.getNestedClassName(definition.getTypeId()) == null) {
      // allow all tasks as nested elements
      for (final AntTypeDefinition def : getAntFile().getBaseTypeDefinitions()) {
        if (def.isTask()) {
          myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
        }
      }
    }
    */
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntAllTasksContainer[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
