import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;

public class ParserDefinitionWithLegalCoreTokenSet implements ParserDefinition {
  public static final TokenSet COMMENTS = TokenSet.EMPTY;
  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
