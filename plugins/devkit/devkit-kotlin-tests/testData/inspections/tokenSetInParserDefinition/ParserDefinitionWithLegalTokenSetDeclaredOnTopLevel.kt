import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

val comments: TokenSet = TokenSet.create(MyLangTokenTypes.COMMENT)

class ParserDefinitionWithLegalTokenSetDeclaredOnTopLevel : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return comments
  }
}
