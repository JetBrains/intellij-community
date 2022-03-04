package org.jetbrains.deft.intellijWs

annotation class IjWsEntity(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjWsAbstractEntity(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjWsSealedData(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjWsData(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjWsObject(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjWsEnum(
    val name: String = "",
    val prefix: String = "com.intellij.workspaceModel.storage.bridgeEntities"
)

annotation class IjById

annotation class Ij(val customName: String = "")

annotation class IjWsId(val className: String)

annotation class IjFile

annotation class IjByParent

annotation class IjSkip