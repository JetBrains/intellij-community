// K2_ERROR: Label does not denote a reachable loop.
// K2_AFTER_ERROR: Label does not denote a reachable loop.
fun test() {
    b@ while (true)
    <caret>a@ {
        break
        break@b
        break@a
    }
}