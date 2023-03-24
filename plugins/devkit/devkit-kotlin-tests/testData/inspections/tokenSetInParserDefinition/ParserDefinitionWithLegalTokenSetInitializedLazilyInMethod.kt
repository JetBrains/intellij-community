import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithLegalTokenSetInitializedLazilyInMethod : ParserDefinition {
  private var comments: TokenSet? = null
  override fun getCommentTokens(): TokenSet {
    if (comments == null) {
      comments = TokenSet.create(MyLangTokenTypes.COMMENT)
    }
    return comments!!
  }
}
