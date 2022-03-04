package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.lines
import org.jetbrains.deft.codegen.ijws.fields.wsCode
import org.jetbrains.deft.codegen.kotlin.writer.toplevel.code
import org.jetbrains.deft.codegen.kotlin.writer.toplevel.metaRef
import org.jetbrains.deft.codegen.utils.fileContents
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.impl.ObjModule
import storage.codegen.patcher.KtObjModule

fun KtObjModule.Built.wsModuleCode(): String = fileContents(
    src.id.javaPackage,
    """
import org.jetbrains.deft.impl.* 
                        
object ${src.id.objName}: ${ObjModule::class.fqn}(${ObjModule.Id::class.fqn}("${src.id.notation}")) {
    @InitApi
    override fun init() {            
        ${src.dependencies.lines("        ") { "requireDependency(${fqn(id.javaPackage, id.objName)})" }}
                    
        beginInit(${src.lastId})
        ${typeDefs.lines("        ") { "add(${fqn(packageName, name)})" }}
    }
}

${extFields.lines { wsCode }}

${typeDefs.filter { !it.abstract }.lines { "fun ${wsFqn("WorkspaceEntityStorageBuilder")}.modifyEntity(entity: ${fqn(packageName, name)}, modification: $name.Builder.() -> Unit) = modifyEntity(${fqn(packageName, "${name}Impl")}.Builder::class.java, entity, modification)" }}
"""
)