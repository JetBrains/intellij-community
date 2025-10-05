

// Here K2 has Disabled: true instead
class A {
    operator fun get(x: Int) {}
    operator fun set(x: String, value: Int) {}

    fun d(x: Int) {
        this["", 1<caret>] = 1
    }
}
