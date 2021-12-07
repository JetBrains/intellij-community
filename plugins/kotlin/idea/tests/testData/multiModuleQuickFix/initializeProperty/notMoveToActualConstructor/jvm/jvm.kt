// "Move to constructor parameters" "false"
// ACTION: Add getter
// ACTION: Add initializer
// ACTION: Convert member to extension
// ACTION: Convert property to function
// ACTION: Introduce backing property
// ACTION: Make 'b' 'abstract'
// ACTION: Move to companion object
// ERROR: Property must be initialized or be abstract

actual class SimpleWConstructor actual constructor(i: Int) {
    actual val <caret>b: String
}