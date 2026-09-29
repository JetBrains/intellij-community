// "Add 'in' variance" "false"
// Variance modifiers cannot be added when type parameter appears in its own bounds
class Mapper<<caret>F: Enum<F>>() {
    fun map(from: F) = null
}
