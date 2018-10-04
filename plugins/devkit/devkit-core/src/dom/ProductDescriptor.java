// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

public interface ProductDescriptor extends DomElement {
  @NotNull
  @Required
  GenericAttributeValue<String> getCode();

  @NotNull
  @Required
  GenericAttributeValue<String> getReleaseDate();

  @NotNull
  @Required
  GenericAttributeValue<Integer> getReleaseVersion();
}
