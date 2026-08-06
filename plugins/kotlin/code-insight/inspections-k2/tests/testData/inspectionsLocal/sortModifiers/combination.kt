// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers

private context(i: Int)
@Depre<caret>cated("Deprecated")
        /**
         * documentation
         */
val foo: Int
    get() = i