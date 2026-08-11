// "Add non-null asserted (arg!!) call" "false"
// K2_AFTER_ERROR: UNSAFE_CALL
// K2_ERROR: UNSAFE_CALL

fun foo(arg: String?) {
    if (arg == null) {
        arg<caret>.length
    }
}