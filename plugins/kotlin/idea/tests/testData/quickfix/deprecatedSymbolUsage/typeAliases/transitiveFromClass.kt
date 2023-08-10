// "Replace with 'NewClass'" "false"
// ACTION: Compiler warning 'TYPEALIAS_EXPANSION_DEPRECATION' options
// ACTION: Convert to block body
// ACTION: Introduce import alias
// ACTION: Remove explicit type specification


@Deprecated("", ReplaceWith("NewClass"))
class OldClass
typealias Old = OldClass

class NewClass

fun foo(): <caret>Old = null!!