// "Add non-null asserted (arg!!) call" "false"

fun foo(arg: String?) {
    if (arg == null) {
        arg<caret>.length
    }
}