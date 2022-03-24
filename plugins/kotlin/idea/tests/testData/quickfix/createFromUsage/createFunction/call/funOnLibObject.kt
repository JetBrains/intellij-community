// "Create extension function 'Unit.foo'" "true"
// WITH_STDLIB

fun test() {
    val a: Int = Unit.<caret>foo(2)
}