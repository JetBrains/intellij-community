// WITH_STDLIB
// HIGHLIGHT: INFORMATION
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

data class PackageWithSource(val name: String, val version: String, val source: String, val id: String)

val packages = listOf<PackageWithSource>().mapIndexed { i, <caret>p -> p.id to i }.toMap()
