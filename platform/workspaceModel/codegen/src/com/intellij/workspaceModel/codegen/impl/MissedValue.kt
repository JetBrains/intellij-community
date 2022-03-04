package org.jetbrains.deft.impl

import org.jetbrains.deft.impl.fields.Field

class MissedValue(val obj: ObjImpl?, val field: Field<*, *>?, val src: MissedValue?) :
    Error("${path(obj, field, src)} should be initialized") {

    val path: String
        get() = path(obj, this.field, src)

    override fun getStackTrace(): Array<StackTraceElement> {
        return if (src == null) super.getStackTrace()
        else arrayOf()
    }
}

fun path(obj: ObjImpl?, field: Field<*, *>?, src: MissedValue?): String {
    if (field == null) return src!!.path
    else {
        val n = field.name
        return if (src != null) "${src.path}.$n" else "$obj.$n"
    }
}