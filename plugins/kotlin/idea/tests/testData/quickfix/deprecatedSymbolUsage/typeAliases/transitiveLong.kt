// "Replace with 'NewClass'" "false"
// ACTION: Compiler warning 'TYPEALIAS_EXPANSION_DEPRECATION' options
// ACTION: Convert to block body
// ACTION: Enable 'Types' inlay hints
// ACTION: Introduce import alias
// ACTION: Introduce local variable


@Deprecated("", replaceWith = ReplaceWith("NewClass"))
class OldClass()

typealias Old1 = OldClass
typealias Old2 = Old1

class NewClass()

fun foo() = <caret>Old2()