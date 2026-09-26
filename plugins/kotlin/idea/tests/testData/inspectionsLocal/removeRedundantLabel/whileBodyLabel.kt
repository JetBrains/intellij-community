// K2_AFTER_ERROR: NOT_A_LOOP_LABEL
// K2_ERROR: NOT_A_LOOP_LABEL
fun test() {
    b@ while (true)
    <caret>a@ {
        break
        break@b
        break@a
    }
}