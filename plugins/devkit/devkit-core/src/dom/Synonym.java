// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

public interface Synonym extends DomElement {
  @NotNull
  @Required(false)
  GenericAttributeValue<String> getText();

  @NotNull
  @Required(false)
  GenericAttributeValue<String> getKey();
}

