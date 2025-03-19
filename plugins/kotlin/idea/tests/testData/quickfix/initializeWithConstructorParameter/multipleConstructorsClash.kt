// IGNORE_K1
// "Initialize with constructor parameter" "true"
class User {
    constructor()
    constructor(blah: String): this()
    constructor(blah: String, n: Int, default: Int = 2)
    constructor(d: Double): this("", 1)
    constructor(n: Int)

    val userN<caret>ame: String
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix