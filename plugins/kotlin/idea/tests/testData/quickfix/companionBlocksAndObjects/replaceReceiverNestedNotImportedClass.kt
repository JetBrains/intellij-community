// "Replace the receiver with 'Nested'" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
// K2_ERROR: UNRESOLVED_REFERENCE
package foo.bar

class Outer {
    class Nested {
        companion {
            fun test() {}
        }
    }
}

fun m(n: Outer.Nested) {
    n.te<caret>st()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ReplaceInstanceReceiverWithClassNameFix
