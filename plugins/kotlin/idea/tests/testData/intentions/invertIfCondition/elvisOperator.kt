fun f(v: String){}

fun test() {
    val isEmpty = false
    if<caret> (isEmpty ?: true) {
        f("Empty")
    } else {
        f("Not empty")
    }
}