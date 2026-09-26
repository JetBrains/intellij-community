// AFTER-WARNING: The expression is unused
// AFTER-WARNING: The expression is unused

class Test {
    val a = "test"

    fun foo() {
        <caret>if (a == "test1") {
            "test1"
        } else if (this.a == "test2") {
            "test2"
        }
    }
}