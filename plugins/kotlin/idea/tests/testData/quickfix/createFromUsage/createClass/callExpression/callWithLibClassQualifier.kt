// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

fun test() {
    val a = 2.<caret>Foo(1)
}