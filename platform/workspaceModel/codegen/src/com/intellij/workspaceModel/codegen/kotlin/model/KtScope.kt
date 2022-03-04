package storage.codegen.patcher

class KtScope(val parent: KtScope?, var owner: Any? = null) {
    val ktInterface: KtInterface? get() = owner as? KtInterface

    override fun toString(): String =
        if (parent == null) owner.toString() else "$parent.${owner.toString()}"

    val isRoot get() = parent == null

    // for merging package related files into one scope
    var sharedScope: KtScope? = null
    val parts = mutableListOf<KtScope>()

    val _own = mutableMapOf<String, KtScope>()
    val own: Map<String, KtScope> get() = _own

    // import x.*
    val importedScopes = mutableListOf<KtScope>()

    // import x.y
    val importedNames = mutableMapOf<String, KtPackage>()

    val file: KtFile?
        get() {
            var i = this
            while (i.owner !is KtFile) i = i.parent ?: return null
            return i.owner as KtFile
        }

    fun def(name: String, value: KtScope) {
        _own[name] = value
        sharedScope?._own?.let { it[name] = value }
    }

    fun resolve(typeName: String): KtScope? {
        var result: KtScope? = this
        typeName.split('.').forEach { 
            result = result?.resolveSimpleName(it)
        }

        return result
    }

    private fun resolveSimpleName(typeName: String): KtScope? {
        val names = sharedScope?._own ?: _own
        return names[typeName]
            ?: resolveFromImportedNames(typeName)
            ?: resolveFromImportedScopes(typeName)
            ?: parent?.resolve(typeName)
    }

    private fun resolveFromImportedNames(typeName: String) =
        importedNames[typeName]?.scope?.resolve(typeName)

    private fun resolveFromImportedScopes(typeName: String): KtScope? {
        importedScopes.forEach {
            val i = it.resolve(typeName)
            if (i != null) return i
        }

        return null
    }

    fun visitTypes(result: MutableList<DefType>) {
        own.values.forEach { inner ->
            inner.ktInterface?.objType?.let {
                result.add(it)
                inner.visitTypes(result)
            }
        }
    }

    fun visitSimpleTypes(result: MutableList<DefType>) {
        own.values.forEach { inner ->
            inner.ktInterface?.simpleType?.let {
                result.add(it)
                inner.visitSimpleTypes(result)
            }
        }
    }
}