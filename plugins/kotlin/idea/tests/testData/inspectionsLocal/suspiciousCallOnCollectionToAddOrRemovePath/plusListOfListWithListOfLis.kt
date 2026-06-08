// PROBLEM: none

// WITH_STDLIB

data class Dependency(val linkerOpts: List<String>)

fun findEmbeddableOptions(options: List<String>): List<List<String>> = TODO()

fun test(linkerKonanFlags: List<String>, allNativeDependencies: List<Dependency>) {
    val optionsToEmbed = findEmbeddableOptions(linkerKonanFlags) <caret>+
            allNativeDependencies.flatMap { findEmbeddableOptions(it.linkerOpts) }
}