// WITH_RUNTIME
// SKIP_ERRORS_BEFORE
// AFTER-WARNING: Parameter 'v' is never used

class Owner {
    var <caret>p: Int
      set(v) {}
}
