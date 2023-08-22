import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenSetInitializedInCompanionObjectInitBlock : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }

  companion object {
    val COMMENTS: TokenSet

    init {
      COMMENTS = TokenSet.EMPTY
    }
  }
}
