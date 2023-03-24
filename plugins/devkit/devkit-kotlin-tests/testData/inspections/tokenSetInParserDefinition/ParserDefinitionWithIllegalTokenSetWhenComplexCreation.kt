import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithIllegalTokenSetWhenComplexCreation : ParserDefinition {
  val <warning descr="TokenSet in ParserDefinition references non-core classes">COMMENTS</warning> = TokenSet.orSet(TokenSet.create(MyLangTokenTypes.COMMENT), TokenSet.create(MyLangTokenTypes.COMMENT))
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
}
