// "Move to constructor" "true"
class Complex(x: Int, y: Double, z: String) {
    /**
     * Very complex field x
     */
    val <caret>x = x
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.MovePropertyToConstructorIntention