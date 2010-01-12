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
package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

/**
 * @author mike
 */
public interface ExtensionPoint extends DomElement {
  enum Area {
    IDEA_PROJECT,
    IDEA_MODULE,
    IDEA_APPLICATION
  }

  @NotNull
  @NameValue
  GenericAttributeValue<String> getName();

  @Attribute("qualifiedName")
  GenericAttributeValue<String> getQualifiedName();

  @NotNull
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getInterface();

  @NotNull
  @Attribute("beanClass")
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getBeanClass();

  @NotNull
  GenericAttributeValue<Area> getArea();
}
