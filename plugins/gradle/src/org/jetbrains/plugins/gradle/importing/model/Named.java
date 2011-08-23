package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:34 PM
 */
public interface Named {

  @NotNull
  String getName();

  void setName(@NotNull String name);
}
