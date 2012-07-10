package com.jetbrains.gettext.lang;

import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextCompositeElementTypes;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class MsgctxtCommand extends MsgCommand {
  public MsgctxtCommand() {
  }

  @Override
  public IElementType getCompositeElement() {
    return GetTextCompositeElementTypes.MSGCTXT;
  }

  @Override
  public String getName() {
    return "msgctxt";
  }
}
