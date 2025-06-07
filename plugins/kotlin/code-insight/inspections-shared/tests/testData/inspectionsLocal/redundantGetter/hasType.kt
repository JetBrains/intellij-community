// ERROR: This property must either have a type annotation, be initialized or be delegated
// K2_ERROR: Abstract property must have an explicit type.
interface Test {
    val foo
        <caret>get(): Int
}