// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// IGNORE_K1
// K2_ERROR: Unresolved reference 't'.
val <T> T.foo: T get() = t

fun test() {
    var a: <caret>Any = "".foo
    a = 1
}