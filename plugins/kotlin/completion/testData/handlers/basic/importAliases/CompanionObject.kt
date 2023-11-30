import kotlin.Int.Companion.MIN_VALUE as MinInt

fun foo() {
    val v: Int = MinI<caret>
}

// IGNORE_K2
// ELEMENT: "MinInt"