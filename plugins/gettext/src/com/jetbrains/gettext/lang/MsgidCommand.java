package com.jetbrains.gettext.lang;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextCompositeElementTypes;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MsgidCommand extends MsgCommand {

  public MsgidCommand() {
  }

  @Override
  public IElementType getCompositeElement() {
    return GetTextCompositeElementTypes.MSGID;
  }

  @Override
  public boolean isNecessary() {
    return true;
  }

  @Override
  public String getName() {
    return "msgid";
  }
}
