import com.intellij.psi.tree.TokenSet;
import com.example.MyLangTokenTypes;

public class NotAParserDefinitionWithIllegalTokenSet { // NOT ParserDefinition
  public static final TokenSet COMMENTS = TokenSet.create(MyLangTokenTypes.COMMENT);

  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
