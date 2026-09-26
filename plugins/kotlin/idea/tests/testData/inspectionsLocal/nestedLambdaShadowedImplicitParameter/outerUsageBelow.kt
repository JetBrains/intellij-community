// FIX: Replace 'it' with explicit parameter


class Foo {
    fun test() {
        "".let {
            "".let { it<caret> }
            it
        }
    }
}
