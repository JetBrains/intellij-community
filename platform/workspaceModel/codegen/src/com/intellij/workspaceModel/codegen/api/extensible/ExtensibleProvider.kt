package org.jetbrains.deft.impl

import org.jetbrains.deft.obj.api.extensible.Extensible

interface ExtensibleProvider {
    fun getExtensibleContainer(): Extensible
}