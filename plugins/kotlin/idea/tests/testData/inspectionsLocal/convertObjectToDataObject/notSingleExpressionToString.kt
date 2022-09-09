// LANGUAGE_VERSION: 1.8
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String {
        "foo".hashCode()
        return "Foo"
    }
}
