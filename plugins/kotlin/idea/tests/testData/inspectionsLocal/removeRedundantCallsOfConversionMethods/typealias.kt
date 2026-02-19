// WITH_STDLIB
typealias MyByte = Byte

fun test(param: MyByte) {
    val byte = param.to<caret>Byte()
}