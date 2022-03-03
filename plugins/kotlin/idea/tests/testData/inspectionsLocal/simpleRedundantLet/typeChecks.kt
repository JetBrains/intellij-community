// PROBLEM: none
// WITH_STDLIB

interface Base

class A : Base
class B : Base

fun isAB(arg: Base) = arg.let<caret> { it is A || it is B }