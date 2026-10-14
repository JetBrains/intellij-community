// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String?)
fun fooNullable() {}

context(_: String)
fun fooNonNull() {}

fun test(value: String?) {
    with(value) {
        foo<caret>
    }
}

// EXIST: fooNullable
// ABSENT: fooNonNull
