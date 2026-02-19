fun foo(s: String): String {
    <caret>if (s == "a") {
        return "a"
    } else if (s == "b") {
        return "b"
    } else if (s == "c") {
        return "c"
    }



    return "d"
}
