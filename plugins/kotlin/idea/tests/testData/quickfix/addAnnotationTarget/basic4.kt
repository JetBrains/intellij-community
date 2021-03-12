// MUTED: "Muted till 1.4.32"
// "Add annotation target" "true"

annotation class Foo

@Foo
class Test {
    @Foo
    fun foo(): <caret>@Foo Int = 1
}