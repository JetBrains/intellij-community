// COMPILER_ARGUMENTS: -Xcontext-parameters

context(_: T)
fun <T> List<T>.fooGenericContext() {}

context(_: String)
fun <T> List<T>.fooStringContext() {}

fun test() {
    with("") {
        listOf(1).foo<caret>
    }
}

// EXIST: fooStringContext
// ABSENT: fooGenericContext
