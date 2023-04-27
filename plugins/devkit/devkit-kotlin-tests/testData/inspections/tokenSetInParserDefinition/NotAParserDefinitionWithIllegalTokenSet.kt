import com.example.MyLangTokenTypes
import com.intellij.psi.tree.TokenSet

class NotAParserDefinitionWithIllegalTokenSet { // NOT ParserDefinition
  val COMMENTS = TokenSet.create(MyLangTokenTypes.COMMENT)
  fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
}
