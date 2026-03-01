import com.intellij.psi.tree.IElementType

class MyTokens {
  val <warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">myToken</warning> = IElementType("MY_TOKEN")
}
