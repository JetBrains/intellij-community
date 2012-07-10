package com.jetbrains.gettext.lang;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextCompositeElementTypes;
import com.jetbrains.gettext.GetTextTokenTypes;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MsgstrCommand extends MsgCommand {

  private int count;

  public MsgstrCommand() {
    count = 0;
  }

  @Override
  public IElementType getCompositeElement() {
    return GetTextCompositeElementTypes.MSGSTR;
  }

  @Override
  public void parse(PsiBuilder builder) throws CommandFormatException {
    try {
      super.parse(builder);
    }
    catch (CommandFormatException e) {
      parseBraces(builder);
    }
    //return super.parse(builder) || parseBraces(builder);
  }

  private boolean parseBraces(PsiBuilder builder) throws CommandFormatException {
    if (builder.getTokenType() == GetTextTokenTypes.LBRACE) {
      builder.advanceLexer();
      if (builder.getTokenType() == GetTextTokenTypes.NUMBER) {
        builder.advanceLexer();
        if (builder.getTokenType() == GetTextTokenTypes.RBRACE) {
          super.parse(builder);
        }
      }
    }
    return false;
  }

  @Override
  public boolean isNecessary() {
    return true;
  }

  @Override
  public boolean isMultiple() {
    return true;
  }

  @Override
  public String getName() {
    return "msgstr";
  }

  @Override
  public int getCount() {
    return count;
  }

  @Override
  public boolean register() {
    boolean result = super.register();
    if (result) {
      count++;
    }
    return result;
  }
}
