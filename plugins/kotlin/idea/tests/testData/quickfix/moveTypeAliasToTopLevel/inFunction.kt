// "Move typealias to top level" "true"
fun bar() {
    <caret>typealias Foo = String

    fun baz(foo: Foo) {
    }
}

fun qux() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeAliasToTopLevelFix