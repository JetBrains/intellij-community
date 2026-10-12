// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String, _: Int)
fun fooBoth() {}

context(_: String, _: Boolean)
fun fooMissingArgument() {}

context(_: String)
fun fooString() {}

fun test() {
    with("") {
        with(1) {
            foo<caret>
        }
    }
}

// EXIST: fooBoth
// EXIST: fooString
// ABSENT: fooMissingArgument
