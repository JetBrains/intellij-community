// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package deft.storage.codegen

import org.jetbrains.deft.codegen.kotlin.writer.toplevel.code
import org.jetbrains.deft.codegen.kotlin.writer.toplevel.metaRef
import org.jetbrains.deft.codegen.utils.fileContents
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.impl.ObjModule
import org.jetbrains.deft.codegen.model.KtObjModule

fun KtObjModule.Built.objCode(): String = fileContents(
    src.id.javaPackage,
    """
import org.jetbrains.deft.impl.*                        
                        
object ${src.id.objName}: ${ObjModule::class.fqn}(${ObjModule.Id::class.fqn}("${src.id.notation}")) {
    @InitApi
    override fun init() {            
        ${src.dependencies.lines("        ") { "requireDependency(${fqn(id.javaPackage, id.objName)})" }}
                    
        beginInit(${src.lastId})
        ${typeDefs.lines("        ") { "add(${fqn(packageName, name)})" }}
        
        beginExtFieldsInit(${extFields.maxOfOrNull { it.id.localId } ?: "0"})
        ${extFields.lines("        ")  { "registerExtField($metaRef)" } }        
    }
}

${extFields.lines { code }}
"""
)