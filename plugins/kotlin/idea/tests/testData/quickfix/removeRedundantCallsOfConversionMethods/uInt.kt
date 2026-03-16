// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test(i: UInt) {
    val foo = i.toUInt()<caret>
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix