// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class WorkingContextProvider {
  public static final ExtensionPointName<WorkingContextProvider> EP_NAME = ExtensionPointName.create("com.intellij.tasks.contextProvider");

  /**
   * Short unique name.
   * Should be valid as a tag name (for serialization purposes).
   * No spaces, dots etc allowed.
   *
   * @return provider's name
   */
  @NotNull
  public abstract String getId();

  /**
   * Short description (for UI)
   * @return
   */
  @NotNull
  public abstract String getDescription();

  /**
   * Saves a component's state.
   * May delegate to {@link com.intellij.openapi.util.JDOMExternalizable#writeExternal(org.jdom.Element)}
   * @param toElement
   */
  public abstract void saveContext(@NotNull Element toElement);

  public abstract void loadContext(@NotNull Element fromElement);

  public void clearContext() {
  }
}
