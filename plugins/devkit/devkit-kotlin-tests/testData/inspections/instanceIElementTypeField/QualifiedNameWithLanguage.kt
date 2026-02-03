import com.intellij.lang.Language

class Foo2 {
  val <warning descr="Suspicious instance field with IElementType initializer. Consider replacing it with a constant">type</warning> = com.intellij.psi.tree.IElementType("foo", Language.ANY)
}
