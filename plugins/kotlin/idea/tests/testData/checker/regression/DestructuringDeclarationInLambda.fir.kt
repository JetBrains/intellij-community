data class XY(val x: Int, val y: Int)

fun convert(xy: XY, f: (XY) -> Int) = f(xy)

fun foo() = <error descr="[NO_VALUE_FOR_PARAMETER]">convert { (<error descr="[UNRESOLVED_REFERENCE]">x</error><error descr="Unexpected tokens (use ';' to separate expressions on the same line)"><error descr="Expecting ')'">,</error> y)</error> }</error>
