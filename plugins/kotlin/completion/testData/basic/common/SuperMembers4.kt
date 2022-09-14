// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// FIR_IDENTICAL
// FIR_COMPARISON
open class B {
    open fun foo() {}
    open fun bar() {}
}

class C : B() {
    override fun foo() {
        super.<caret>
    }
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "()", typeText: "Unit", attributes: "bold", icon: "Method"}
// EXIST: { lookupString: "bar", itemText: "bar", tailText: "()", typeText: "Unit", attributes: "bold", icon: "Method"}
// EXIST: equals
// EXIST: hashCode
// EXIST: toString
// NOTHING_ELSE
