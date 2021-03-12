// MUTED: "Muted till 1.4.32"
// "Add annotation target" "true"

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Foo

class Test {
    fun foo(): <caret>@Foo Int = 1
}