// COMPILER_ARGUMENTS: -Xcontext-parameters
// IGNORE_K1

context(sooLong: String)
fun foo(i: Int) {}

fun test() {
    with("context") {
        foo(<caret>)
    }
}

// ABSENT: { itemText: "sooLong =" }
