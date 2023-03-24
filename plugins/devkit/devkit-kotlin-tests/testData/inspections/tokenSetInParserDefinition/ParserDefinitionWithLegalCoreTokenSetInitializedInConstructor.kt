import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenSetInitializedInConstructor : ParserDefinition {
  val comments: TokenSet

  init {
    comments = TokenSet.EMPTY
  }

  override fun getCommentTokens(): TokenSet {
    return comments
  }
}
