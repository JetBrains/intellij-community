// "Create expected class in common module testModule_Common" "false"
// DISABLE_ERRORS

class Some(val x: Int, val y: Int) {
    fun processIt(z: Int) = x + y - z
}

actual typealias <caret>My = Some