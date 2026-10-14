// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String)
fun fooString() {}

context(_: Int)
fun fooInt() {}

fun fooNoContext() {}

fun test() {
    foo<caret>
}

// EXIST: fooNoContext
// ABSENT: fooString
// ABSENT: fooInt
