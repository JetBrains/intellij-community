package com.intellij.util.pbxproj;

import com.intellij.lexer.FlexAdapter;
import com.intellij.psi.tree.IElementType;


public class PbxLexer extends FlexAdapter {
  public PbxLexer() {
    super(new _PbxprojLexer());
  }

  public void nextTokenOrComment() {
    advance();
    do {
      final IElementType tt = getTokenType();
      if (tt != PbxTokenType.WHITE_SPACE) break;
      advance();
    }
    while (true);
  }

  public void nextToken() {
    advance();
    do {
      final IElementType tt = getTokenType();
      if (tt != PbxTokenType.WHITE_SPACE && tt != PbxTokenType.COMMENT) break;
      advance();
    }
    while (true);
  }
}
