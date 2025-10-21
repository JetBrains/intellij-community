// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2
// Issue: KTIJ-34938

interface Config {
    val debug: Boolean
}

context(<caret>_: Config)
val isDebugMode: Boolean
    get() = contextOf<Config>().debug

fun example(config: Config) {
    context(config) {
        if (isDebugMode) {
        }
    }
}
