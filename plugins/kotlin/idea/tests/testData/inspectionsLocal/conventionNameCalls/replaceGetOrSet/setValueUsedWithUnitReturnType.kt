// PROBLEM: none

class C {
    operator fun set(s: String, value: Int): Unit = Unit
}

class D(val c: C) {
    fun foo() {
        return this.c.<caret>set("x", 1)
    }
}
