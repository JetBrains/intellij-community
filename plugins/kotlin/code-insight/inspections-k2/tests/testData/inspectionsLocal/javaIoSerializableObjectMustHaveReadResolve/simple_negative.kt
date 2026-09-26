// PROBLEM: none
object Foo<caret> : java.io.Serializable {
    fun readResolve(): Any = Foo
}
