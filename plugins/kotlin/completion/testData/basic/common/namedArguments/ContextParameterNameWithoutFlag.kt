// COMPILER_ARGUMENTS: -Xcontext-parameters


context(sooLong: String)
fun foo(i: Int) {}

fun test() {
    with("context") {
        foo(<caret>)
    }
}

// ABSENT: { itemText: "sooLong =" }
