fun test() {
    bar(11, <caret>l@(todo()/*comment*/), "")
}

fun todo(): Nothing = throw Exception()

fun bar(i: Int, s: String, a: Any) {}
