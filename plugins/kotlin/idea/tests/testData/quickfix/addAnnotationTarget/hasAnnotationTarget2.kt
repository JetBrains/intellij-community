// MUTED: "Muted till 1.4.32"
// "Add annotation target" "true"

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class Foo

@Foo
class Test {
    @Foo
    fun foo(): <caret>@Foo Int = 1
}