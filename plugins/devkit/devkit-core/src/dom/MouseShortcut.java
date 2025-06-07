// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter;
import org.jetbrains.idea.devkit.dom.keymap.KeymapXmlRootElement;

@Presentation(icon = "AllIcons.General.Mouse")
public interface MouseShortcut extends DomElement {

  @NotNull
  @Required
  @Convert(KeymapConverter.class)
  GenericAttributeValue<KeymapXmlRootElement> getKeymap();

  @NotNull
  @Required
  @NoSpellchecking
  GenericAttributeValue<String> getKeystroke();

  @NotNull
  GenericAttributeValue<Boolean> getRemove();

  @NotNull
  GenericAttributeValue<Boolean> getReplaceAll();
}
