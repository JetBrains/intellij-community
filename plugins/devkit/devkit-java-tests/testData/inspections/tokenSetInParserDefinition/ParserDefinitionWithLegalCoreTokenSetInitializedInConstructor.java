import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;

public class ParserDefinitionWithLegalCoreTokenSetInitializedInConstructor implements ParserDefinition {
  public final TokenSet comments;

  public ParserDefinitionWithLegalCoreTokenSetInitializedInConstructor() {
    comments = TokenSet.EMPTY;
  }

  @Override
  public TokenSet getCommentTokens() {
    return comments;
  }
}
