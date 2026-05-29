@RequiresOptIn
annotation class ReqOptinAnnotation

class X {
    @set:OptIn(<caret>ReqOptinAnnotation::class)
    var x: Int = 0
        get() = field
        set(value) {
            field = value
        }
}