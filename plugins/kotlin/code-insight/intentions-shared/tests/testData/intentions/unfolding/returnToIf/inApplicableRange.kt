fun test(b: Boolean): Int {
    var i = 0
    while (i == 0) {
        return if<caret> (b) {
            1
        } else {
            i++
            continue
        }
    }
    return 0
}
