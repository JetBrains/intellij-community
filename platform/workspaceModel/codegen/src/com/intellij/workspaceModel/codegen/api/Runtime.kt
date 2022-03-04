package org.jetbrains.deft.runtime

import org.jetbrains.deft.Root
import org.jetbrains.deft.impl.ObjModule

object Runtime : ObjModule(Id("org.jetbrains.deft.runtime")) {
    @InitApi
    override fun init() {
        beginInit(1)
        add(Root)
    }
}