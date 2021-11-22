// WITH_RUNTIME
import Enum.*

enum class Enum {
    A, B, C
}

fun ordinal(e : Enum) {
    if (e == Enum.A) {
        if (<warning descr="Condition is always true"><weak_warning descr="Value is always zero">e.ordinal</weak_warning> == 0</warning>) {}
    }
}

fun foo(<warning descr="[UNUSED_PARAMETER] Parameter 'x' is never used">x</warning>: Int) {}

fun bar() {
    foo(if (<warning descr="Condition is always true">Enum.A == Enum.A</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always false">Enum.A != Enum.A</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">A == A</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B > Enum.A</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B >= Enum.A</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B >= Enum.B</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B < Enum.C</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B <= Enum.C</warning>) 1 else 2)
    foo(if (<warning descr="Condition is always true">Enum.B <= Enum.B</warning>) 1 else 2)
}

fun test(e1 : Enum, e2 : Enum, e3 : Enum) {
    if (e1 < e2) {
        if (e2 < e3) {
            if (<warning descr="Condition is always false">e3 < e1</warning>) {}
        }
        if (<warning descr="Condition is always false">e1 >= e2</warning>) {}
        if (<warning descr="Condition is always false">e2 == A</warning>) {}
    }
}

