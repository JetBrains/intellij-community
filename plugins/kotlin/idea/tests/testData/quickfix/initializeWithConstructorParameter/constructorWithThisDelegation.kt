// "Initialize with constructor parameter" "true"
open class RGrandAccessor(x: Int) {}

open class RAccessor : RGrandAccessor {
    <caret>val f: Int
    constructor(p: Boolean) : super(1)
    constructor(p: String) : this(true)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.InitializePropertyQuickFixFactory$InitializeWithConstructorParameter
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.InitializePropertyQuickFixFactories$InitializeWithConstructorParameterFix