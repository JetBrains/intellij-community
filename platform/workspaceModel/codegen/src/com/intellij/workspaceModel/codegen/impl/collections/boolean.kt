package org.jetbrains.deft.collections

import kotlinx.io.core.Input
import kotlinx.io.core.Output

fun Output.writeBoolean(exported: Boolean) {
    writeByte(if (exported) 1 else 0)
}

fun Input.readBoolean(): Boolean =
    readByte() == 1.toByte()
