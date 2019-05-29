/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import com.intellij.psi.xml.XmlFile;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter;

public interface KeyboardShortcut extends DomElement {

  @NotNull
  @Required
  @NoSpellchecking
  GenericAttributeValue<String> getFirstKeystroke();

  @NotNull
  @Required
  @Convert(KeymapConverter.class)
  GenericAttributeValue<XmlFile> getKeymap();

  @NotNull
  @NoSpellchecking
  GenericAttributeValue<String> getSecondKeystroke();

  @NotNull
  GenericAttributeValue<Boolean> getRemove();

  @NotNull
  GenericAttributeValue<Boolean> getReplaceAll();
}
