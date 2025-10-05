// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -Xcontext-parameters

class Entity<T>() {
    constructor(<caret>t: T) : this()
}
