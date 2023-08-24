// "Move to constructor" "true"
class Complex(x: Int, y: Double, z: String) {
    val <caret>y: Double = y // Duplicating
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention