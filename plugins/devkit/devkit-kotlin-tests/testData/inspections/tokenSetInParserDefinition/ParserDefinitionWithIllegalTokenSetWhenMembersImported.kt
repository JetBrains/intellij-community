import com.intellij.lang.ParserDefinition
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.TokenSet.create
import com.example.MyLangTokenTypes.COMMENT

class ParserDefinitionWithIllegalTokenSetWhenMembersImported : ParserDefinition {
  val <warning descr="TokenSet in ParserDefinition references non-platform classes">COMMENTS</warning> = create(COMMENT)
  override fun getCommentTokens(): TokenSet {
    return COMMENTS
  }
}
