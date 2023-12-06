import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;
import com.example.MyLangTokenTypes;

public class ParserDefinitionWithIllegalTokenSetWhenComplexCreation implements ParserDefinition {
  public static final TokenSet <warning descr="TokenSet in ParserDefinition references non-platform classes">COMMENTS</warning> = TokenSet.orSet(TokenSet.create(MyLangTokenTypes.COMMENT), TokenSet.create(MyLangTokenTypes.COMMENT));

  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
