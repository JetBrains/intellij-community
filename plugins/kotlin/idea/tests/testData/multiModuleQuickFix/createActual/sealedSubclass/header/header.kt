// "Add missing actual declarations" "true"
// IGNORE_K2

expect sealed class Sealed

expect object Obj : Sealed

expect class <caret>Klass(x: Int) : Sealed
