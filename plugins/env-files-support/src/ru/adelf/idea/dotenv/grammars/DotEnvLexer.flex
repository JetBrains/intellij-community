package ru.adelf.idea.dotenv.grammars;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static ru.adelf.idea.dotenv.psi.DotEnvTypes.*;

%%

%{
  public _DotEnvLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _DotEnvLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT="//".*
VALUE=.+

%%
<YYINITIAL> {
  {WHITE_SPACE}       { return WHITE_SPACE; }

  "SPACE"             { return SPACE; }

  {LINE_COMMENT}      { return LINE_COMMENT; }
  {VALUE}             { return VALUE; }

}

[^] { return BAD_CHARACTER; }
