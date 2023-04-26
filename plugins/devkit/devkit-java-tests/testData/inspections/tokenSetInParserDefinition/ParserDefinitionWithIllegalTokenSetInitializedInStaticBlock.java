import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.TokenSet;
import com.example.MyLangTokenTypes;

public class ParserDefinitionWithIllegalTokenSetInitializedInStaticBlock implements ParserDefinition {
  public static final TokenSet <warning descr="TokenSet in ParserDefinition references non-platform classes">COMMENTS</warning>;

  static {
    COMMENTS = TokenSet.create(MyLangTokenTypes.COMMENT);
  }

  @Override
  public TokenSet getCommentTokens() {
    return COMMENTS;
  }
}
