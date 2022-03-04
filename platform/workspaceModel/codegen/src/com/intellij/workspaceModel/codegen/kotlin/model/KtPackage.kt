package storage.codegen.patcher

class KtPackage(val fqn: String?) {
    override fun toString(): String = "[package: ${fqn ?: "<anonymous>"}]"

    val scope = KtScope(null, this)
    val files = mutableListOf<KtFile>()

    init {
        scope.owner = this
    }
}