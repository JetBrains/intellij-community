// PROBLEM: none
object Foo<caret> : java.io.Serializable {
    private fun readResolve(): Any = Foo
}