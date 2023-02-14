// WITH_STDLIB
// SKIP_ERRORS_BEFORE
// AFTER-WARNING: Parameter 'value' is never used
class Owner {
    var <caret>p: Int
        get() = 1
        set
}
