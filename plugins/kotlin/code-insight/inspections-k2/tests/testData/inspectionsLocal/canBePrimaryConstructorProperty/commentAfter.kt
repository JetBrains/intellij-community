// FIX: Move to constructor
class Complex(x: Int, y: Double, z: String) {
    val <caret>y: Double = y // Duplicating
}