package org.jetbrains.deft.impl

import org.jetbrains.deft.ObjStorage

class SourceImpl(val name: String) : ObjStorage.Source {
    override fun toString(): String = name
}