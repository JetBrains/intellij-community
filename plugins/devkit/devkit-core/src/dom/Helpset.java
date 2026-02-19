// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated This interface is used only to highlight usages of the deprecated tag.
 */
@Deprecated
@ApiStatus.Internal
public interface Helpset extends DomElement {

  @Required
  @NotNull GenericAttributeValue<String> getFile();

  @Required
  @NotNull GenericAttributeValue<String> getPath();
}
