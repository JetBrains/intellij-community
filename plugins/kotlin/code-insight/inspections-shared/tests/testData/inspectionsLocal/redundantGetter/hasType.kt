
// K2-ERROR: Abstract property must have an explicit type.
interface Test {
    val foo
        <caret>get(): Int
}