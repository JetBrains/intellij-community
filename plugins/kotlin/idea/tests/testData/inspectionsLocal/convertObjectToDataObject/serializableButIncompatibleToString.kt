// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
object<caret> Foo : java.io.Serializable {
    override fun toString(): String = "FOO"
}
