// IS_APPLICABLE: false
interface C {
    operator fun set(p: String, value: Int)
}

class D(val c: C) {
    fun foo() {
        this.c[""] = 10<caret> //is not visible
    }
}
