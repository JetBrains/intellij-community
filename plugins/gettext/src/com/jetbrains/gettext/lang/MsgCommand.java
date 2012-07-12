package com.jetbrains.gettext.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextTokenTypes;

/**
 * @author Svetlana.Zemlyanskaya
 */
public abstract class MsgCommand {

  protected boolean exists = false;

  public abstract IElementType getCompositeElement();

  public boolean parse(PsiBuilder builder) {
    builder.advanceLexer();
    int count = 0;
    while (builder.getTokenType() == GetTextTokenTypes.STRING) {
      checkString(builder);
      count++;
    }
    return count > 0;
  }

  private static void checkString(PsiBuilder builder) {
    String text = builder.getTokenText();
    if (text != null && text.length() > 0 &&
        (text.charAt(0) != '\"' || text.charAt(text.length() - 1) != '\"')) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      marker.error("Unbounded string");
    } else {
      builder.advanceLexer();
    }
  }

  public boolean isNecessary() {
    return false;
  }

  public boolean isMultiple() {
    return false;
  }

  public boolean exists() {
    return exists;
  }

  public boolean register() {
    if (!exists) {
      exists = true;
      return true;
    }
    return isMultiple();
  }

  public abstract String getName();
}
