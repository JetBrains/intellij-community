import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;

public class ParserDefinitionWithLegalCoreTokenSetInitializedInStaticBlock implements ParserDefinition {
  public static final TokenSet COMMENTS;

  static {
    COMMENTS = TokenSet.EMPTY;
  }

  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
