// "Convert member to extension" "true"
// WITH_STDLIB
interface B {
    <caret>inline fun foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertMemberToExtensionFix
// TODO: KTIJ-29625
// IGNORE_K2