/*
 * @author max
 */
package com.intellij.util.pbxproj;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

public interface PbxTokenType extends TokenType {
  IElementType COMMENT = new IElementType("COMMENT", PbxLanguage.INSTANCE);
  IElementType STRING_LITERAL = new IElementType("STRING_LITERAL", PbxLanguage.INSTANCE);
  IElementType HEX_LITERAL = new IElementType("HEX_LITERAL", PbxLanguage.INSTANCE);
  IElementType VALUE = new IElementType("VALUE", PbxLanguage.INSTANCE);
  IElementType LBRACE = new IElementType("LBRACE", PbxLanguage.INSTANCE);
  IElementType RBRACE = new IElementType("RBRACE", PbxLanguage.INSTANCE);
  IElementType LPAR = new IElementType("LPAR", PbxLanguage.INSTANCE);
  IElementType RPAR = new IElementType("RPAR", PbxLanguage.INSTANCE);
  IElementType EQ = new IElementType("EQ", PbxLanguage.INSTANCE);
  IElementType COMMA = new IElementType("COMMA", PbxLanguage.INSTANCE);
  IElementType SEMICOLON = new IElementType("SEMICOLON", PbxLanguage.INSTANCE);
}
