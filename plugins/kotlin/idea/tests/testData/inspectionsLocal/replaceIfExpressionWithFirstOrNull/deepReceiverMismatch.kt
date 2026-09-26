// PROBLEM: none
// WITH_STDLIB

class Branch(val size: Int, val c: List<String>)

class Root(val b: Branch)

fun test(a: Root): String? {
    return <caret>if (a.b.size > 0) {
        a.b.c[0]
    } else {
        null
    }
}
