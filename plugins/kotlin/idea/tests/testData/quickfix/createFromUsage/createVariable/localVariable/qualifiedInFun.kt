// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'A'.
// K2_AFTER_ERROR: Unresolved reference 'foo' on receiver of type 'A'.

class A

fun test(a: A) {
    val t: Int = a.<caret>foo
}