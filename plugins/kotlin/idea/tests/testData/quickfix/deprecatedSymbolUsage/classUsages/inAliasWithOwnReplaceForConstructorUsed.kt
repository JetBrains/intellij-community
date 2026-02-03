// "Replace with 'NewClass'" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

package aa

@Deprecated("", ReplaceWith("NewClass"))
class OldClass @Deprecated("", ReplaceWith("NewClass(12)")) constructor()

class NewClass(p: Int)

// No apply, error instead
typealias Old = <caret>OldClass

val a: Old = aa.Old()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2