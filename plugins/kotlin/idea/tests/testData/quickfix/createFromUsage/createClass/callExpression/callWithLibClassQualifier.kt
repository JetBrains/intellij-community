// "Create class 'Foo'" "false"
// ERROR: Unresolved reference: Foo
// WITH_STDLIB

fun test() {
    val a = 2.<caret>Foo(1)
}