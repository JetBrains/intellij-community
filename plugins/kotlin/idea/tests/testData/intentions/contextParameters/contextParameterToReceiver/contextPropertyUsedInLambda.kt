// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// Issue: KTIJ-34938

interface Formatter {
    fun format(value: Any): String
}

context(<caret>_: Formatter)
val formatterFunction: (String) -> String
    get() = { input -> contextOf<Formatter>().format(input) }

fun example(formatter: Formatter) {
    context(formatter) {
        val formatter = { formatterFunction }
    }
}
