// FIX: Remove explicit type arguments
// WITH_STDLIB

class B(val isReady: Boolean, val enabled: Boolean)

typealias B2Boolean = (B) -> Boolean

fun main(args: Array<String>) {
    val listOfFuns1 = listOf<B2Boolean><caret>(
        {b: B -> b.isReady && b.enabled},
        B::isReady,
        {b: B -> true }
    )
}