@file:JvmName("BaseEntityModifications")
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract

@GeneratedCodeApiVersion(3)
interface BaseEntityBuilder<T: BaseEntity>: WorkspaceEntityBuilder<T>{
override var entitySource: EntitySource
var name: String
var moduleId: SimpleId
var aBaseEntityProperty: String
var dBaseEntityProperty: String
var bBaseEntityProperty: String
var sealedDataClassProperty: BaseDataClass
}
