// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB

data class ContentData(
    val pluginId: String,
    val includedModules: List<String>,
    val moduleLibraries: List<String>,
    val projectLibraries: List<String>,
    val modules: List<String>,
    val productModules: List<String>,
    val productEmbeddedModules: List<String>,
    val contentModules: List<String>,
    val library: String?,
    val module: String?,
)

fun deserializeContentData(): List<ContentData> = emptyList()

fun test(modules: MutableSet<String>) {
    for (<caret>entry in deserializeContentData()) {
        modules.addAll(entry.productModules)
        modules.addAll(entry.productEmbeddedModules)
        entry.module?.let(modules::add)
    }
}
