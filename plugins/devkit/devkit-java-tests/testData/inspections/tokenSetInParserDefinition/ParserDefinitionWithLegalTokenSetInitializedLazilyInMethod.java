import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;
import com.example.MyLangTokenTypes;

public class ParserDefinitionWithLegalTokenSetInitializedLazilyInMethod implements ParserDefinition {
  private TokenSet comments;
  @Override
  public TokenSet getCommentTokens() {
    if (comments == null) {
      comments = TokenSet.create(MyLangTokenTypes.COMMENT);
    }
    return comments;
  }
}
