// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface JsonObject extends JsonValue {

  @NotNull
  List<JsonProperty> getPropertyList();

  @Nullable
  JsonProperty findProperty(String name);

}
