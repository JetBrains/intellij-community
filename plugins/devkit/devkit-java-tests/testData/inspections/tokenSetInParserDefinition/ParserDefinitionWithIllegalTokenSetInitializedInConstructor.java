import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;
import com.example.MyLangTokenTypes;

public class ParserDefinitionWithIllegalTokenSetInitializedInConstructor implements ParserDefinition {
  public final TokenSet <warning descr="TokenSet in ParserDefinition references non-core classes">comments</warning>;

  public ParserDefinitionWithIllegalTokenSetInitializedInConstructor() {
    comments = TokenSet.create(MyLangTokenTypes.COMMENT);
  }

  @Override
  public TokenSet getCommentTokens() {
    return comments;
  }
}
