package com.jetbrains.json;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.jetbrains.json.JsonParserTypes.*;

public class JsonSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new Highlighter();
  }

  private static class Highlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();

    static {
      ATTRIBUTES.put(L_CURLY, DefaultLanguageHighlighterColors.BRACES);
      ATTRIBUTES.put(R_CURLY, DefaultLanguageHighlighterColors.BRACES );
      ATTRIBUTES.put(L_BRAKET, DefaultLanguageHighlighterColors.BRACKETS );
      ATTRIBUTES.put(R_BRAKET, DefaultLanguageHighlighterColors.BRACES );
      ATTRIBUTES.put(COMMA,  DefaultLanguageHighlighterColors.COMMA);
      ATTRIBUTES.put(COLON,  DefaultLanguageHighlighterColors.SEMICOLON);
      ATTRIBUTES.put(STRING, DefaultLanguageHighlighterColors.STRING);
      ATTRIBUTES.put(NUMBER, DefaultLanguageHighlighterColors.NUMBER);
      ATTRIBUTES.put(TRUE, DefaultLanguageHighlighterColors.KEYWORD);
      ATTRIBUTES.put(FALSE, DefaultLanguageHighlighterColors.KEYWORD);
      ATTRIBUTES.put(NULL, DefaultLanguageHighlighterColors.KEYWORD);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
      return new FlexAdapter(new _JsonLexer());
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType type) {
      return pack(ATTRIBUTES.get(type));
    }
  }
}
