// IGNORE_FIR
// Here K2 has Disabled: true instead
class A {
    operator fun get(x: Int) {}
    operator fun set(x: String, value: Int) {}

    fun d(x: Int) {
        this["", 1<caret>] = 1
    }
}

/*
Text: (x: String), Disabled: false, Strikeout: false, Green: true
*/
