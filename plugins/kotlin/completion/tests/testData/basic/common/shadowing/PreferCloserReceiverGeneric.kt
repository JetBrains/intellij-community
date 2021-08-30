// FIR_IDENTICAL
// FIR_COMPARISON
interface I<T> {
    fun xxx(): T
}

fun foo(i1: I<Int>, i2: I<String>) {
    with (i1) {
        with (i2) {
           xx<caret>
        }
    }
}

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "String", icon: "nodes/abstractMethod.svg"}
// NOTHING_ELSE
