package org.jetbrains.deft.codegen.ijws

import deft.storage.codegen.lines
import org.jetbrains.deft.codegen.utils.fileContents
import storage.codegen.patcher.KtObjModule

fun KtObjModule.Built.ijWsCode() = fileContents(
    "org.jetbrains.deft.intellijWs",
    """
import com.intellij.workspaceModel.storage.bridgeEntities.*
    
${typeDefs.lines("") { toIjWsCode() }}                
    """.trim()
)
