fun t() {
    val a: Int? = 1
    if (true) {<caret>
        (a as Int).toString()
    }
}