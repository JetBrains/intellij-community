import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithIllegalTokenSetInCompanionObject : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
  companion object {
    val <warning descr="TokenSet in ParserDefinition references non-core classes">COMMENTS</warning> = TokenSet.create(MyLangTokenTypes.COMMENT)
  }
}
