package com.intellij.openapi.vcs.changes.ignore.lexer;

import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreLanguage;
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType;
import org.jetbrains.annotations.Nullable;
import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.*;
%%

%{
  private IgnoreLanguage language;

  public IgnoreLexer(@Nullable VirtualFile virtualFile) {
    this((java.io.Reader)null);

    if (virtualFile != null && virtualFile.getFileType() instanceof IgnoreFileType) {
      this.language = ((IgnoreFileType) virtualFile.getFileType()).getIgnoreLanguage();
    }
  }

  private boolean isSyntaxSupported() {
    return language == null || language.isSyntaxSupported();
  }
%}

%public
%class IgnoreLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

CRLF            = "\r"|"\n"|"\r\n"
LINE_WS         = [\ \t\f]
WHITE_SPACE     = ({LINE_WS}*{CRLF}+)+

HEADER          = ###[^\r\n]*
SECTION         = ##[^\r\n]*
COMMENT         = #[^\r\n]*
NEGATION        = \!
SLASH           = \/
BRACKET_LEFT    = \[
BRACKET_RIGHT   = \]

FIRST_CHARACTER = [^!# ]
SYNTAX_KEY      = "syntax:"
VALUE           = ("\\\["|"\\\]"|"\\\/"|[^\[\]\r\n\/])+

%state IN_ENTRY

%%
<YYINITIAL> {
    {WHITE_SPACE}+      { yybegin(YYINITIAL); return CRLF; }
    {LINE_WS}+          { return CRLF; }
    {HEADER}            { return HEADER; }
    {SECTION}           { return SECTION; }
    {COMMENT}           { return COMMENT; }

    {NEGATION}          { return NEGATION; }
    {SYNTAX_KEY}        {
            if ( isSyntaxSupported() ) {
                return SYNTAX_KEY;
            } else {
                yybegin(IN_ENTRY);
                yypushback(yylength());
            }
        }
    {FIRST_CHARACTER}   { yypushback(1); yybegin(IN_ENTRY); }
}

<IN_ENTRY> {
    {WHITE_SPACE}+      { yybegin(YYINITIAL); return CRLF; }
    {BRACKET_LEFT}      { yybegin(IN_ENTRY); return BRACKET_LEFT; }
    {BRACKET_RIGHT}     { yybegin(IN_ENTRY); return BRACKET_RIGHT; }
    {SLASH}             { yybegin(IN_ENTRY); return SLASH; }

    {VALUE}             { yybegin(IN_ENTRY); return VALUE; }
}
