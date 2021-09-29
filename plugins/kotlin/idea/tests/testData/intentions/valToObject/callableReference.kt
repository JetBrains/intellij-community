// IS_APPLICABLE: false
// AFTER-WARNING: Variable 'ref' is never used
interface B {
}

val <caret>a = object : B {
}

fun foo() {
    val ref = ::a
}
