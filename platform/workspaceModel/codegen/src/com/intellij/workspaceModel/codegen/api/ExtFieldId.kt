package org.jetbrains.deft.impl.fields

import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.bytes.readString
import org.jetbrains.deft.bytes.writeString
import org.jetbrains.deft.impl.ObjModule

data class ExtFieldId(val moduleId: ObjModule.Id, val localId: Int) {
    override fun toString(): String = "${moduleId.notation}:$localId"

    fun writeTo(output: Output) {
        output.writeString(moduleId.notation)
        output.writeInt(localId)
    }

    companion object {
        fun read(data: Input) =
            ExtFieldId(
                ObjModule.Id(data.readString()),
                data.readInt()
            )
    }
}