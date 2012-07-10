package com.jetbrains.gettext.lang;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextCompositeElementTypes;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MsgidPluralCommand extends MsgCommand {
  public MsgidPluralCommand() {
  }
  @Override
  public IElementType getCompositeElement() {
    return GetTextCompositeElementTypes.MSGID_PLURAL;
  }

  @Override
  public String getName() {
    return "msgid_plural";
  }
}
