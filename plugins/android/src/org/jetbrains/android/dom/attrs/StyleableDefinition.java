package org.jetbrains.android.dom.attrs;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface StyleableDefinition {

  @NotNull
  List<StyleableDefinition> getChildren();

  @NotNull
  String getName();

  @NotNull
  List<AttributeDefinition> getAttributes();
}
