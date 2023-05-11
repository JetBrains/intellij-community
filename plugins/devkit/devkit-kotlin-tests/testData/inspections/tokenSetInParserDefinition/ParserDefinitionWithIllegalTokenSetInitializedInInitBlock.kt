import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithIllegalTokenSetInitializedInInitBlock : ParserDefinition {
  val <warning descr="TokenSet in ParserDefinition references non-platform classes">comments</warning>: TokenSet

  init {
    comments = TokenSet.create(MyLangTokenTypes.COMMENT)
  }

  override fun getCommentTokens(): TokenSet {
    return comments
  }
}
