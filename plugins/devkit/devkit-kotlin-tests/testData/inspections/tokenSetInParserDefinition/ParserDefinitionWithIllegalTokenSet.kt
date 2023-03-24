import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithIllegalTokenSet : ParserDefinition {
  val <warning descr="TokenSet in ParserDefinition references non-core classes">COMMENTS</warning> = TokenSet.create(MyLangTokenTypes.COMMENT)
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
}
