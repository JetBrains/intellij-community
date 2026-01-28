// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix" "false"
abstract class NewClass(val i: () -> Int)

@Deprecated("Text", ReplaceWith("NewClass({i})"))
abstract class OldClass(val i: Int)

class F : OldClass<caret> {
    constructor(i: Int) : super(i)
    constructor(i: () -> Int) : super(i())
}