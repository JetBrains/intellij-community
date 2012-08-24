package org.jetbrains.android.dom.attrs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public interface AttributeDefinitions {
  @Nullable
  StyleableDefinition getStyleableByName(@NotNull String name);

  @NotNull
  Set<String> getAttributeNames();

  @Nullable
  AttributeDefinition getAttrDefByName(@NotNull String name);

  @NotNull
  StyleableDefinition[] getStateStyleables();
}
