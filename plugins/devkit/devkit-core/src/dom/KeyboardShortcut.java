// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.KeymapConverter;
import org.jetbrains.idea.devkit.dom.keymap.KeymapXmlRootElement;

public interface KeyboardShortcut extends DomElement {

  @NotNull
  @Required
  @NoSpellchecking
  GenericAttributeValue<String> getFirstKeystroke();

  @NotNull
  @Required
  @Convert(KeymapConverter.class)
  GenericAttributeValue<KeymapXmlRootElement> getKeymap();

  @NotNull
  @NoSpellchecking
  GenericAttributeValue<String> getSecondKeystroke();

  @NotNull
  GenericAttributeValue<Boolean> getRemove();

  @NotNull
  GenericAttributeValue<Boolean> getReplaceAll();
}
