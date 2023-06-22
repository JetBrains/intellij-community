// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
data object Foo<caret> : java.io.Serializable {
    fun readResolve(): Any = Foo
}
