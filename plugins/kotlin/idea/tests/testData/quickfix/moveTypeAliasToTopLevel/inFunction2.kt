// "Move typealias to top level" "true"
fun bar() {
    class C {
        <caret>typealias Foo = String

        fun baz(foo: Foo) {
        }
    }
}

fun qux() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeAliasToTopLevelFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeAliasToTopLevelFix