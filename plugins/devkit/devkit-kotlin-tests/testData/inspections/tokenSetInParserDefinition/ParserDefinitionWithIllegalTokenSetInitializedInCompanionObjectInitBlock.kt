import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.example.MyLangTokenTypes

class ParserDefinitionWithIllegalTokenSetInitializedInCompanionObjectInitBlock : ParserDefinition {
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }

  companion object {
    val <warning descr="TokenSet in ParserDefinition references non-platform classes">COMMENTS</warning>: TokenSet

    init {
      COMMENTS = TokenSet.create(MyLangTokenTypes.COMMENT)
    }
  }
}
