import com.intellij.lang.ParserDefinition;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;

public class ParserDefinitionWithLegalCoreTokenTypeInitializedInConstructor implements ParserDefinition {
  public final TokenSet comments;

  public ParserDefinitionWithLegalCoreTokenTypeInitializedInConstructor() {
    comments = TokenSet.create(TokenType.WHITE_SPACE);
  }

  @Override
  public TokenSet getCommentTokens() {
    return comments;
  }
}
