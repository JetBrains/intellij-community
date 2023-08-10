import com.intellij.lang.ParserDefinition
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenTypeInitializedInStaticBlock : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }

  companion object {
    val COMMENTS: TokenSet

    init {
      COMMENTS = TokenSet.create(TokenType.WHITE_SPACE)
    }
  }
}
