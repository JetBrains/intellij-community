// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String)
fun fooString() {}

context(_: Int)
fun fooInt() {}

context(value: String)
fun test() {
    foo<caret>
}

// EXIST: fooString
// ABSENT: fooInt
