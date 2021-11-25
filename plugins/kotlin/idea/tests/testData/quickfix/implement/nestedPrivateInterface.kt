// "Implement interface" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION
// WITH_STDLIB

class Container {
    private interface <caret>Base {
        var z: Double
    }
}