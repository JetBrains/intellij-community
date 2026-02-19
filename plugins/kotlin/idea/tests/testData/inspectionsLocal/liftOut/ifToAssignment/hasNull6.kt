fun bar(i: Int, x: String?) {
    var str: String? = null

    <caret>if (i == 1) {
        str = null
    } else if (i == 2) {
        str = "2"
    } else {
        str = x
    }
}
