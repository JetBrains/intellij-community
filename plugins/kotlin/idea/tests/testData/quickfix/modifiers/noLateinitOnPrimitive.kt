// "Add 'lateinit' modifier" "false"
// WITH_STDLIB
// ACTION: Add initializer
// ACTION: Add full qualifier
// ACTION: Introduce import alias
// ACTION: Make 'a' 'abstract'
// ACTION: Move to constructor parameters
// ACTION: Move to constructor
// ACTION: Add getter
// ACTION: Add getter and setter
// ACTION: Add setter
// ERROR: Property must be initialized or be abstract

class A {
    private var a: Int<caret>
}
