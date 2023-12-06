// "Add missing actual declarations" "true"
// IGNORE_K2

expect sealed class <caret>Sealed() {
    object Obj : Sealed

    class Klass(x: Int) : Sealed
}