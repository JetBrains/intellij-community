// "Create expected class in common module testModule_Common" "false"
// DISABLE-ERRORS
// ACTION: Apply all 'Remove modifier' fixes in file
// ACTION: Remove 'actual' modifier

class Some(val x: Int, val y: Int) {
    fun processIt(z: Int) = x + y - z
}

actual typealias <caret>My = Some