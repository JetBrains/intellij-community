@file:Suppress("PackageDirectoryMismatch")

package org.jetbrains.deft

import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

class RootImpl : ObjImpl(), Root {
    override fun checkInitialized() = Unit
    override fun hasNewValue(field: Field<*, *>): Boolean = true
    override val factory: ObjType<*, *>
        get() = Root

    class Builder(override val result: RootImpl = RootImpl()) : ObjBuilderImpl<Root>(), Root, Root.Builder, ExtensibleProvider {
        override val factory: ObjType<Root, *> get() = Root
        override val name: String? get() = null
        override val parent: Obj? get() = null
        override fun build(): Root = result
        override fun getExtensibleContainer(): ExtensibleImpl = result
    }
}