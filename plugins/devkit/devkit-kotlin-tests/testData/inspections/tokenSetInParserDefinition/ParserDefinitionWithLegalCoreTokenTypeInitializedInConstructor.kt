import com.intellij.lang.ParserDefinition
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenTypeInitializedInConstructor : ParserDefinition {
  val comments: TokenSet

  init {
    comments = TokenSet.create(TokenType.WHITE_SPACE)
  }

  override fun getCommentTokens(): TokenSet {
    return comments
  }
}
