// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
val foo = Int.MIN_VALUE.toInt()<caret>
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix