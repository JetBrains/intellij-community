// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
val foo = Short.MAX_VALUE.toShort<caret>()
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix