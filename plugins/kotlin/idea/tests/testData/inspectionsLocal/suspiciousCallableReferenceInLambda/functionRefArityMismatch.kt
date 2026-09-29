// FIX: none
// WITH_STDLIB

class Holder {
    fun method(a: String, b: Int): Boolean = true
}

fun test(h: Holder) {
    "".let {<caret> h::method }
}
