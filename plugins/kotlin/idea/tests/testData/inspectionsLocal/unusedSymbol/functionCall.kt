// PROBLEM: none
class K {
    fun a8<caret>() {}
}

fun t(h: K.() -> Unit) {
}

fun j() {
    t {
        a8()
    }
}