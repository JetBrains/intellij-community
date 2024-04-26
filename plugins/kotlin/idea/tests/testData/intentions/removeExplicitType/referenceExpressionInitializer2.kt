// IS_APPLICABLE: false
// ERROR: Unresolved reference: t
// IGNORE_K1
val <T> T.foo: T get() = t

fun test() {
    var a: <caret>Any = "".foo
    a = 1
}