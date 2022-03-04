package storage.codegen.patcher

import deft.storage.codegen.generatedApiCode
import org.jetbrains.deft.codegen.patcher.visitRecursively

class KtFile(val module: KtObjModule, val name: String, val content: () -> CharSequence) {
    override fun toString(): String = "[file://$name]"
    fun asSrc() = Src(name, content)

    lateinit var pkg: KtPackage private set
    lateinit var imports: KtImports private set
    val scope = KtScope(null, this)
    val block = KtBlock(asSrc(), null)

    init {
        scope.owner = this
    }

    fun setPackage(p: String?) {
        pkg = module.getOrCreatePackage(p)
        pkg.files.add(this)
        scope.sharedScope = pkg.scope
        pkg.scope.parts.add(scope)
    }

    fun setImports(imports: KtImports) {
        this.imports = imports
        import()
    }

    private fun import() {
        imports.list.forEach {
            val pt = it.lastIndexOf('.')
            val pkg: String?
            val name: String
            if (pt != -1) {
                pkg = it.substring(0 until pt)
                name = it.substring(pt + 1 until it.length)
            } else {
                pkg = null
                name = it
            }

            val importedPackage = module.getOrCreatePackage(pkg)
            if (name == "*") scope.importedScopes.add(importedPackage.scope)
            else scope.importedNames[name] = importedPackage
        }
    }
}