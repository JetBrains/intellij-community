fun main() {
    val randomFunction: (x: kotlin.properties.ObservableProperty<Int>, y: kotlin.String) -> kotlin.String = { <caret>x, str -> str}
}

// WITH_STDLIB
// AFTER-WARNING: Parameter 'x' is never used, could be renamed to _
// AFTER-WARNING: Variable 'randomFunction' is never used
