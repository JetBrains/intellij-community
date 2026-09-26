fun nonExhaustiveInt(x: Int) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(x) {
    0 -> false
}

fun nonExhaustiveBoolean(b: Boolean) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(b) {
    false -> 0
}

fun nonExhaustiveNullableBoolean(b: Boolean?) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(b) {
    false -> 0
    true -> 1
}

enum class Color { 
    RED,
    GREEN,
    BLUE
}

fun nonExhaustiveEnum(c: Color) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(c) {
    Color.GREEN -> 0xff00
}

fun nonExhaustiveNullable(c: Color?) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(c) {
    Color.RED -> 0xff
    Color.BLUE -> 0xff0000
}

fun whenOnEnum(c: Color) {
    <error descr="[NO_ELSE_IN_WHEN]">when</error>(c) {
        Color.BLUE -> {}
        Color.GREEN -> {}
    }
}

enum class EnumInt {
    A0, A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, A11, A12, A13, A14, A15
}

fun whenOnLongEnum(i: EnumInt) = <error descr="[NO_ELSE_IN_WHEN]">when</error> (i) {
    EnumInt.A7 -> 7
}

sealed class Variant {
    object Singleton : Variant()
   
    class Something : Variant()

    object Another : Variant()
}

fun nonExhaustiveSealed(v: Variant) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(v) {
    Variant.Singleton -> false
}

fun nonExhaustiveNullableSealed(v: Variant?) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(v) {
    Variant.Singleton -> false
    is Variant.Something -> true
}

sealed class Empty

fun nonExhaustiveEmpty(e: Empty) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(<warning descr="[UNUSED_EXPRESSION]">e</warning>) {}

fun nonExhaustiveNullableEmpty(e: Empty?) = <error descr="[NO_ELSE_IN_WHEN]">when</error>(<warning descr="[UNUSED_EXPRESSION]">e</warning>) {}
