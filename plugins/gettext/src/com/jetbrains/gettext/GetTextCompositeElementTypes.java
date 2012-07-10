package com.jetbrains.gettext;

import com.intellij.psi.tree.IElementType;

/**
 * @author Svetlana.Zemlyanskaya
 */
public interface GetTextCompositeElementTypes {
  IElementType HEADER = new GetTextCompositeElementType("HEADER");

  IElementType MSG_BLOCK = new GetTextCompositeElementType("MSG_BLOCK");
  IElementType MSG_CONTENT = new GetTextCompositeElementType("MSG_CONTENT");
  IElementType MSGID = new GetTextCompositeElementType("MSGID");
  IElementType MSGSTR = new GetTextCompositeElementType("MSGSTR");
  IElementType MSGCTXT = new GetTextCompositeElementType("MSGCTXT");
  IElementType MSGID_PLURAL = new GetTextCompositeElementType("MSGID_PLURAL");
}
