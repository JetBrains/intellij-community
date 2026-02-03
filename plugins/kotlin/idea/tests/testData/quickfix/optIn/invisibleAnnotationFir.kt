// "Opt in for 'Ann' on 'test'" "false"
// ERROR: This declaration needs opt-in. Its usage must be marked with '@Foo.Ann' or '@OptIn(Foo.Ann::class)'
// K2_AFTER_ERROR: This declaration needs opt-in. Its usage must be marked with '@Foo.Ann' or '@OptIn(Foo.Ann::class)'
// ACTION: Add import for 'Foo.bar'
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Introduce import alias
object Foo {
    @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
    private annotation class Ann

    @Ann
    fun bar() {}
}

fun test() {
    Foo.bar<caret>()
}
