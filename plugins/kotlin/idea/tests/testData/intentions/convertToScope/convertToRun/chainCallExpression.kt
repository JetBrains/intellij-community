// WITH_STDLIB
// AFTER-WARNING: Parameter 'scriptBuilder' is never used
// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Variable 'resultWithDefaultPlugins' is never used
package foo.bar

class KTest {
    fun test() {
        val context = getContext()
        context.abc(x = "y")
        val resultWithDefaultPlugins =  context
            .xyz()
            .<caret>abc(scriptBuilder = {
                doSmth()
            }, x = "y")
    }

    private fun doSmth() {

    }

    private fun getContext(): Context {
        return Context()
    }
}

class Context {
    fun abc(x: String, scriptBuilder: () -> Unit = {}): Context {
        return this
    }

    fun xyz(): Context {
        return this
    }

}
