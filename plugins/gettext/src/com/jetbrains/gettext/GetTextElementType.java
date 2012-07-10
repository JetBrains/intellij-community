package com.jetbrains.gettext;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextElementType extends IElementType {
  public GetTextElementType(@NotNull @NonNls String debugName) {
    super(debugName, GetTextLanguage.INSTANCE);
  }
}
