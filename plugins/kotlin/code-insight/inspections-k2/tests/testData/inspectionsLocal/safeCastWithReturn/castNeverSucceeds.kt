// AFTER_ERROR: Incompatible types: String and Int
// K2_AFTER_ERROR: Check for instance is always 'true'.
fun test(x: Int) {
    <caret>x as? String //comment1
            ?: return
}
