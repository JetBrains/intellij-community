// WITH_STDLIB
interface CD

fun <D : CD> D.foo(): D? = null

fun test(x: List<CD>) {
    x.mapNotNull <caret>{ it.foo() }
}