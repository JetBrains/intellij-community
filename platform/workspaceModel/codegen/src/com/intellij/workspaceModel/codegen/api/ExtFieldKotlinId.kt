package org.jetbrains.deft.obj.api

import org.jetbrains.deft.impl.ObjType

data class ExtFieldKotlinId(val receiver: ObjType<*, *>, val name: String)