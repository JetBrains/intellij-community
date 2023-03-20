// WITH_STDLIB
class Test {
    val test = run {<caret>foo()

    fun foo(): Int {
        return 42
    }
}
