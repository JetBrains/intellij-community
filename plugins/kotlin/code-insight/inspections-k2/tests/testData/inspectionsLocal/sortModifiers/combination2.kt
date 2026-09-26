// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers

private context(i: Int)
@Deprecated("Deprecated")
        /**
         * <caret>documentation
         */
val foo: Int
    get() = i