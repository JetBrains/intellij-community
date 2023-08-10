// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.context;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class WorkingContextProvider {
  public static final ExtensionPointName<WorkingContextProvider> EP_NAME = new ExtensionPointName<>("com.intellij.tasks.contextProvider");

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
   * Short description (for UI).
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  public abstract String getDescription();

  /**
   * Saves a component's state.
   * May delegate to {@link com.intellij.openapi.util.JDOMExternalizable#writeExternal(Element)}
   *
   */
  public abstract void saveContext(@NotNull Project project, @NotNull Element toElement);

  public abstract void loadContext(@NotNull Project project, @NotNull Element fromElement);

  public void clearContext(@NotNull Project project) {
  }
}
