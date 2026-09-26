// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
// AFTER-WARNING: Variable 'foo' is never used
fun test(i: ULong) {
    val foo = i.toULong()<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix