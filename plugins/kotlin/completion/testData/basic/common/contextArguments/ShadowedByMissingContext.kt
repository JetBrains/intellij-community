// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: String)
fun fooContext() {}

fun test() {
    context(_: Int)
    fun fooContext() {}

    with("") {
        fooContext<caret>
    }
}

// EXIST: fooContext
// NUMBER: 1
