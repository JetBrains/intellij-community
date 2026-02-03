// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ProductDescriptor extends DomElement {

  @NotNull
  @Required
  @NoSpellchecking
  GenericAttributeValue<String> getCode();

  @NotNull
  @Required
  GenericAttributeValue<String> getReleaseDate();

  @NotNull
  @Required
  GenericAttributeValue<String> getReleaseVersion();

  @Nullable
  GenericAttributeValue<Boolean> getOptional();
}
