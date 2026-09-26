// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
val foo = ""
val bar = foo.toString()<caret>
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix