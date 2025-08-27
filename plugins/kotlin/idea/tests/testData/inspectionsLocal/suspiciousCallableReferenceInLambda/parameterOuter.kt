// FIX: Move reference into parentheses
// WITH_STDLIB
// ERROR: Callable reference resolution ambiguity: <br>public final operator fun plus(other: Byte): Int defined in kotlin.Int<br>public final operator fun plus(other: Double): Double defined in kotlin.Int<br>public final operator fun plus(other: Float): Float defined in kotlin.Int<br>public final operator fun plus(other: Int): Int defined in kotlin.Int<br>public final operator fun plus(other: Long): Long defined in kotlin.Int<br>public final operator fun plus(other: Short): Int defined in kotlin.Int
// ERROR: Not enough information to infer type variable R
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.
// K2_ERROR: Overload resolution ambiguity between candidates:<br>fun plus(other: Byte): Int<br>fun plus(other: Short): Int<br>fun plus(other: Int): Int<br>fun plus(other: Long): Long<br>fun plus(other: Float): Float<br>fun plus(other: Double): Double
fun foo(bar: Int) {
    listOf(1,2,3).map {<caret> bar::plus }
}