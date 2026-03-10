// "Initialize with constructor parameter" "true"
// K2_ERROR: Property must be initialized or be abstract.

class User {
    constructor()
    constructor(blah: String)

    val userN<caret>ame: String
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix