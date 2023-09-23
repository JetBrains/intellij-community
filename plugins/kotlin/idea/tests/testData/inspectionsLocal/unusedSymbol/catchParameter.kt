// IGNORE_K1

class Foo {
    fun foo() {
        try {
            println();
        } catch (e<caret>xc: Throwable) {
        }
    }
}