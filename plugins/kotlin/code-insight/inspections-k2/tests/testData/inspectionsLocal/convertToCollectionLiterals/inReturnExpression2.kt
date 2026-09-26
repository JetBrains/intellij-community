// COMPILER_ARGUMENTS: -Xcollection-literals
fun test(x: Int, y: Int): Array<Int?> {
    return arrayOf<caret>(x, y, null)
}