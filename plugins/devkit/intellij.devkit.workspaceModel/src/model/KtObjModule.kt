// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.deft.codegen.model

import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.impl.fields.ExtField
import storage.codegen.patcher.KotlinReader

class KtObjModule(
    fqn: Id,
    val addDependencies: List<KtObjModule> = listOf()
) : ObjModule(fqn) {
    val moduleObjName = fqn.objName
    val packages = mutableMapOf<String?, KtPackage>()
    val files = mutableListOf<KtFile>()
    val extFields = mutableListOf<ExtField<*, *>>()
    fun getOrCreatePackage(p: String?): KtPackage = packages.getOrPut(p) { KtPackage(p) }

    init {
        addDependencies.forEach { otherModule ->
            otherModule.packages.values.forEach { packageToImport ->
                getOrCreatePackage(packageToImport.fqn)
                    .scope.importedScopes.add(packageToImport.scope)
            }
        }
    }

    fun addFile(name: String, content: () -> String): KtFile {
        val file = KtFile(this, name, content)
        val reader = KotlinReader(file)
        reader.read()
        files.add(file)
        return file
    }

    @InitApi
    override fun init() {
        // everything should be done in build method
    }

    @OptIn(InitApi::class)
    fun build(diagnostics: Diagnostics = Diagnostics()): Built {
        addDependencies.forEach {
            requireDependency(it)
        }

        val simpleTypes = mutableListOf<DefType>()
        files.forEach {
            it.scope.visitSimpleTypes(simpleTypes)
        }
        simpleTypes.forEach { it.def.buildFields(diagnostics) }
        simpleTypes.forEach { it.verify(diagnostics) }

        val types = mutableListOf<DefType>()
        files.forEach {
            it.scope.visitTypes(types)
        }

        types.forEach { it.def.buildFields(diagnostics) }
        types.forEach { it.verify(diagnostics) }
        if (types.isEmpty()) {
            beginInit(0)
        } else {
            beginInit(types.maxOf { it.id })
            types.forEach { add(it) }
        }

        files.forEach { f ->
            f.block.defs.forEach {
                it.toExtField(f.scope, this, diagnostics)
            }
        }

        require()
        return Built(this, types, simpleTypes, extFields)
    }

    private var nextTypeId = 1
    fun nextTypeId() = nextTypeId++

    class Built(
      val src: KtObjModule,
      val typeDefs: List<DefType>,
      val simpleTypes: List<DefType>,
      val extFields: MutableList<ExtField<*, *>>
    )
}