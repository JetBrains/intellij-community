import com.intellij.psi.tree.IElementType

class MyTokens {
  companion object {
    @JvmField
    val myToken = IElementType("MY_TOKEN")
  }
}
