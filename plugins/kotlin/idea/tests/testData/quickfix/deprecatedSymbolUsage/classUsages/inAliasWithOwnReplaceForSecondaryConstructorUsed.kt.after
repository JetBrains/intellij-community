// "Replace with 'NewClass'" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

@Deprecated("", ReplaceWith("NewClass"))
class OldClass constructor() {
    @Deprecated("", ReplaceWith("NewClass(12)")) constructor(i: Int): this()
}

class NewClass(p: Int)

// No apply, error instead
typealias Old = <caret>OldClass

val a: Old = Old(1)
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2