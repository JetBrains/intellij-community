import com.intellij.lang.ParserDefinition;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

public class ParserDefinitionWithLegalCoreTokenType implements ParserDefinition {
  public static final TokenSet COMMENTS = TokenSet.create(TokenType.WHITE_SPACE);
  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
