import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;

import static com.example.MyLangTokenTypes.COMMENT;
import static com.intellij.psi.tree.TokenSet.create;

public class ParserDefinitionWithIllegalTokenSetWhenStaticImportsUsed implements ParserDefinition {
  public static final TokenSet <warning descr="TokenSet in ParserDefinition references non-platform classes">COMMENTS</warning> = create(COMMENT);

  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
