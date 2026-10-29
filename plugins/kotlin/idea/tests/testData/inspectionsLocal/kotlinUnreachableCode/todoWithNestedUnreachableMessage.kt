
// FIX: Remove unreachable code
fun example(flag: Boolean) {
    TODO(
        if (flag) {
            return
            <caret>"Unreachable"
        } else {
            "Message"
        }
    )
}
