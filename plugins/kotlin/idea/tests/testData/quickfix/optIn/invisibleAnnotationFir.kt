// "Opt in for 'Ann' on 'test'" "false"
// IGNORE_K1
// ERROR: This declaration needs opt-in. Its usage must be marked with '@Foo.Ann' or '@OptIn(Foo.Ann::class)'
// ACTION: Add import for 'Foo.bar'
object Foo {
    @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
    private annotation class Ann

    @Ann
    fun bar() {}
}

fun test() {
    Foo.bar<caret>()
}