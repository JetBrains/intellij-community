// "Move typealias to top level" "true"
class C {
    class CC {
        <caret>typealias Foo = String

        fun bar(foo: Foo) {
        }
    }
}

fun baz() {}
// IGNORE_K2
// Nested type aliases are introduced by KT-45285