// FIX: Add explicit parameter name to outer lambda
// IGNORE_K1

class Foo {
    fun test() {
        "".let {
            run {
                "".let { it<caret> }
            }
            it
        }
    }
}
