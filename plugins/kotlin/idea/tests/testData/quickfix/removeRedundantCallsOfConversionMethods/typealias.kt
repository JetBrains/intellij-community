// "Remove redundant calls of the conversion method" "true"
// WITH_STDLIB
typealias MyByte = Byte

fun test(param: MyByte) {
    val byte = param.to<caret>Byte()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantCallsOfConversionMethodsFix