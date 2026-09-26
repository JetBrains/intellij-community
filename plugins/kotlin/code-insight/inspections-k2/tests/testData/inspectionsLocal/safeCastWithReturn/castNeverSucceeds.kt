// AFTER_ERROR: Incompatible types: String and Int
// K2_AFTER_ERROR: IMPOSSIBLE_IS_CHECK_ERROR
fun test(x: Int) {
    <caret>x as? String //comment1
            ?: return
}
