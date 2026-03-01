// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
val foo = Long.MAX_VALUE.toLong()<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix