class Test {
    var foo: Int = 1
        set(value)<caret> {
            bar(field)
        }

    fun bar(i: Int) {}
}