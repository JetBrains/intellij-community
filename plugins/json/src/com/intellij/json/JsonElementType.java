package com.intellij.json;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JsonElementType extends IElementType {
  public JsonElementType(@NotNull @NonNls String debugName) {
    super(debugName, JsonLanguage.INSTANCE);
  }
}
