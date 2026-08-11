// "Move typealias to top level" "true"
// K2_ERROR: UNSUPPORTED_FEATURE
fun bar() {
    class C {
        <caret>typealias Foo = String

        fun baz(foo: Foo) {
        }
    }
}

fun qux() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeAliasToTopLevelFix
// IGNORE_K2
// Nested type aliases are introduced by KT-45285