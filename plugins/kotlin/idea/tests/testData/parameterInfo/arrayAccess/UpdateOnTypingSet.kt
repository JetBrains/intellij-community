class A {
    operator fun get(x: Int) {}
    operator fun set(x: String, y: Int, value: Int) {}

    fun d(x: Int) {
        this[<caret>] = 1
    }
}

// TYPE: "1, "

/*
Text: (x: String, <highlight>y: Int</highlight>), Disabled: true, Strikeout: false, Green: true
*/
