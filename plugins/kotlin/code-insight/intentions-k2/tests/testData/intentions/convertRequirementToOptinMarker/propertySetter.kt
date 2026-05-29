@RequiresOptIn
annotation class ReqOptinAnnotation

class X {
    @set:<caret>ReqOptinAnnotation
    var x: Int = 0
        get() = field
        set(value) {
            field = value
        }
}