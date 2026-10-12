// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String)
fun fooString() {}

context(_: String)
fun fooStringAgain() {}

context(_: Int)
fun fooInt() {}

context(_: Int)
fun fooIntAgain() {}

fun fooNoContext() {}

fun test() {
    with(receiver = "") {
        foo<caret>
    }
}

// EXIST: fooString
// EXIST: fooStringAgain
// EXIST: fooNoContext
// ABSENT: fooInt
// ABSENT: fooIntAgain
