// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

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

fun deserializeContentData(): ContentData = ContentData(
    "",
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    null,
    null,
)

fun test() {
    val <caret>entry = deserializeContentData()
    println(entry.module)
}
