// WITH_STDLIB
// AFTER-WARNING: Variable 'id' is never used
// AFTER-WARNING: Variable 'name' is never used
data class Foo(val id: Int, val name: String)

fun test() {
    listOf(Foo(123, "def"), Foo(456, "abc"))<caret>
}