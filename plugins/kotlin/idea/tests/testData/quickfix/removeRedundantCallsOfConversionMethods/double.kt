// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
val foo = 1.1.toDouble()<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix