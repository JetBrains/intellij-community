// PROBLEM: none

data class SomeName(val a: Int, val b: Int, val c: String)

fun foo(f: SomeName) {
    f.<caret>copy(a = 0, b = 0, c = "")
}