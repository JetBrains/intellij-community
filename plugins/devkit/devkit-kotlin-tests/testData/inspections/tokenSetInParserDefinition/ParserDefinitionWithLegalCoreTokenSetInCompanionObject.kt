import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenSetInCompanionObject : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
  companion object {
    val COMMENTS = TokenSet.EMPTY
  }
}
