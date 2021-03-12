// MUTED: "Muted till 1.4.32"
// "Add annotation target" "true"

annotation class Foo

class Test {
    fun foo(): <caret>@Foo Int = 1
}