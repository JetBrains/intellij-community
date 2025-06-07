// "Add non-null asserted (arg!!) call" "false"
// K2_AFTER_ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'Nothing?'.

fun foo(arg: String?) {
    if (arg == null) {
        arg<caret>.length
    }
}