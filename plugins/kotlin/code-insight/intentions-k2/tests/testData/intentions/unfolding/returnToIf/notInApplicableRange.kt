// IS_APPLICABLE: false

fun test(b: Boolean): Int {
    while (true) {
        return if <caret>(b) {
            1
        } else {
            break
        }
    }
    return 0
}