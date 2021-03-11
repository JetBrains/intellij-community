// "Add annotation target" "true"

annotation class Foo

@Foo
class Test {
    // TODO: [VD] temporary broken due to https://youtrack.jetbrains.com/issue/KT-45417
    @Foo
    fun foo(): @Foo Int = 1
}