import kotlin.math.sqrt

class QuadraticEquationsSolver {
    private fun discriminant(a: Double, b: Double, c: Double) =
        b * b - 4 * a * c

    fun solve(a: Double, b: Double, c: Double) {
        val d = discriminant(a, b, c)
        when {
            d < 0 -> println("No roots")
            d > 0 -> {
                val x1 = (-b + sqrt(d)) / (2.0 * a)
                val x2 = (-b - sqrt(d)) / (2.0 * a)
                println("x1 = $x1, x2 = $x2")
            }
            else -> println("x = ${-b / (2.0 * a)}")
        }
    }
}