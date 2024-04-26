// FIX: Use property access syntax
// LANGUAGE_VERSION: 2.1
fun test(t: Thread) {
    take { t.name } // works

    take(t::<caret>getName)
    take(t::name) // should work, should be no warnings
}

fun interface MyStringSupplier {
    fun doStuff(): String
}

fun take(s: MyStringSupplier) {}