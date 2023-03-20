package sample

import java.util.*

fun <E: Enum<E>> foo(values: Array<E>) {
    EnumSet.noneOf(values.first().declaringClass)
}