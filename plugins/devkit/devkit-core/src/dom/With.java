/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.PsiField;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginFieldNameConverter;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

/**
 * @author yole
 */
public interface With extends DomElement {
  @NotNull
  @Stubbed
  @Attribute("attribute")
  @Convert(PluginFieldNameConverter.class)
  GenericAttributeValue<PsiField> getAttribute();

  @NotNull
  @Attribute("tag")
  @Convert(PluginFieldNameConverter.class)
  GenericAttributeValue<PsiField> getTag();

  @NotNull
  @Stubbed
  @Attribute("implements")
  @Convert(PluginPsiClassConverter.class)
  GenericAttributeValue<PsiClass> getImplements();
}
