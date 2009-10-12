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

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntElementVisitor;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntTaskImpl extends AntStructuredElementImpl implements AntTask {

  public AntTaskImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public AntTaskImpl(final AntElement parent,
                     final XmlTag sourceElement,
                     final AntTypeDefinition definition,
                     @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntTask(this);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTask[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return (!isMacroDefined() && !isPresetDefined() && !isTypeDefined()) ? AntElementRole.TASK_ROLE : AntElementRole.USER_TASK_ROLE;
  }

  @Nullable
  public AntStructuredElement getAntParent() {
    return (AntStructuredElement)super.getAntParent();
  }

  public boolean isMacroDefined() {
    final AntTypeDefinition def = getTypeDefinition();
    return def != null && def.getClassName().startsWith(AntMacroDefImpl.ANT_MACRODEF_NAME);
  }

  public boolean isScriptDefined() {
    final AntTypeDefinition def = getTypeDefinition();
    return def != null && def.getClassName().startsWith(AntScriptDefImpl.ANT_SCRIPTDEF_NAME);
  }
}
