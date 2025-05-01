// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// WITH_STDLIB
// K2_AFTER_ERROR: Unresolved reference 'Foo'.

fun test() {
    val a = 2.<caret>Foo(1)
}