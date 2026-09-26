// WITH_STDLIB
// KTIJ-38076
class Holder {
    val value: String
        get() = "computed"
}

fun test(h: Holder) {
    i<caret>f (h.value == "a") {
        println("a")
    } else if (h.value == "b") {
        println("b")
    } else {
        println("c")
    }
}
