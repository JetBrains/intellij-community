// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Stubbed;
import org.jetbrains.annotations.NotNull;

public interface Actions extends ActionContainer {

  @Stubbed
  @NotNull GenericAttributeValue<String> getResourceBundle();
}
