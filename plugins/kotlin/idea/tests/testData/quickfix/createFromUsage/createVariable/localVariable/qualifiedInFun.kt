// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class A

fun test(a: A) {
    val t: Int = a.<caret>foo
}