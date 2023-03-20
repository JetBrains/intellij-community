// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_IDENTICAL
// FIR_COMPARISON
interface Base {
    fun inBase()
}

interface I1 : Base {
    fun inI1()
}

interface I2 : I1 {
    fun inI2()
}

fun foo(i1: I1, i2: I2) {
    with(i1) {
        with(i2) {
            <caret>
        }
    }
}
// EXIST: { lookupString: "inI1", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "inI2", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "inBase", attributes: "", icon: "nodes/abstractMethod.svg"}
// EXIST: { lookupString: "equals", attributes: "", icon: "Method"}
