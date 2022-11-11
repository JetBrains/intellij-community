// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn

@RequiresOptIn
annotation class Marker

lateinit var variable: RecType<*>

@OptIn(<caret>Marker::class)
fun t() {
    variable
}

interface RecType<R : RecType<R>>
