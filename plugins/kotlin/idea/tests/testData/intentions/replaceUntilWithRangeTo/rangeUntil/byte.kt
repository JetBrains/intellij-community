// WITH_STDLIB
@OptIn(kotlin.ExperimentalStdlibApi::class)
fun test(from: Byte, to: Byte) {
    from..<to<caret>
}