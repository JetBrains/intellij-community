// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
interface I<T> {
    fun foo(t: T): T
}

class A<T> : List<String>, I<T> {
    o<caret>
}

// EXIST: { lookupString: "override", itemText: "override"}
// EXIST: { itemText: "override fun hashCode(): Int {...}", tailText: null, typeText: "Any", attributes: "", icon: "Method"}
// EXIST: { itemText: "override fun foo(t: T): T {...}", tailText: null, typeText: "I", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { itemText: "override fun get(index: Int): String {...}", tailText: null, typeText: "List", attributes: "bold", icon: "nodes/abstractMethod.svg"}
