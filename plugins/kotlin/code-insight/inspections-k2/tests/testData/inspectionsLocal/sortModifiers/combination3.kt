// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers
context(i: Int)
@Deprecated("Deprecated")
        /**
         * documentation
         */<caret>
private val foo: Int
    get() = i