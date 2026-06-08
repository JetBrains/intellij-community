// FIX: Add explicit parameter name to outer lambda


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
