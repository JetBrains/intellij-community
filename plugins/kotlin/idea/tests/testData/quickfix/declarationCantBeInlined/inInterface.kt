// "Convert member to extension" "true"
// WITH_STDLIB
interface B {
    <caret>inline fun foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertMemberToExtensionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertMemberToExtensionFix