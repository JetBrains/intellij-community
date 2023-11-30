// FIR_COMPARISON
// FIR_IDENTICAL
package pack

fun foobarOne(required: Int): Int = required

fun foobarTwo(required: Int, optional: Int = 3): Int = required + optional

fun test(foobarArg: Int) {}

fun runTest() {
    test(foobar<caret>)
}

// EXIST: { lookupString: "foobarOne", itemText: "foobarOne", tailText:"(required: Int) (pack)", icon: "Function"}
// EXIST: { lookupString: "foobarTwo", itemText: "foobarTwo", tailText: "(required: Int, optional: Int = ...) (pack)", icon:"Function"}
// EXIST: { lookupString: "foobarArg =", itemText: "foobarArg =", tailText: " Int", icon: "Parameter"}