import com.intellij.lang.ParserDefinition
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenType : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }

  companion object {
    val COMMENTS = TokenSet.create(TokenType.WHITE_SPACE)
  }
}
