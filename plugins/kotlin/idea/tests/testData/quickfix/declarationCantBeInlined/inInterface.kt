// "Convert member to extension" "true"
// WITH_STDLIB
// K2_ERROR: 'inline' modifier on virtual members is prohibited. Only private or final members can be inlined.
interface B {
    <caret>inline fun foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertMemberToExtensionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertMemberToExtensionFix