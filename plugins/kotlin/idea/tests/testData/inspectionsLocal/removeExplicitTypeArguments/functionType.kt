// FIX: Remove explicit type arguments
// WITH_STDLIB

class B(val isReady: Boolean, val enabled: Boolean)

fun main(args: Array<String>) {
    val listOfFuns2 = listOf<(B) -> Boolean><caret>(
        {b: B -> b.isReady && b.enabled},
        B::isReady,
        {b: B -> true }
    )
}