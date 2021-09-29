// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
class WithParameters {
    constructor(<caret>x: Int = 42, y: String = "Hello")
}