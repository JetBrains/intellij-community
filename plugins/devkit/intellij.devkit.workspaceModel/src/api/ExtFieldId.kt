package org.jetbrains.deft.impl.fields

import org.jetbrains.deft.impl.ObjModule

data class ExtFieldId(val moduleId: ObjModule.Id, val localId: Int) {
    override fun toString(): String = "${moduleId.notation}:$localId"
}