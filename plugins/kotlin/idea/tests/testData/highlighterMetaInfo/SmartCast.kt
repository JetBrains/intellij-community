class My(val x: Int?)

fun My?.foo(): Int {
    if (this == null) return 42
    if (x == null) {
        if (x != null) {
            return x
        }
        return 13
    }
    return x
}
