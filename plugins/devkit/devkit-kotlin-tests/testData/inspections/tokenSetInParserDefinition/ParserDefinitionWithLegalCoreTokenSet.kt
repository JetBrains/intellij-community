import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet

class ParserDefinitionWithLegalCoreTokenSet : ParserDefinition {
  val comments = TokenSet.EMPTY
  override fun getCommentTokens(): TokenSet {
    return comments
  }
}
