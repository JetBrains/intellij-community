// "Add missing actual declarations" "true"

expect sealed class <caret>Sealed() {
    object Obj : Sealed

    class Klass(x: Int) : Sealed
}