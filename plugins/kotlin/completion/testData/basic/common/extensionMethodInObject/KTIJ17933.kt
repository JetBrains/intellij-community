// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
public interface Test {
    public fun String.extension()

    public companion object : Test by TestImpl
}

internal object TestImpl : Test {
    override fun String.extension() = TODO()
}

fun usage() {
    "".ext<caret>
}

// EXIST: { lookupString: "extension", tailText: "() for String in TestImpl (<root>)", itemText: "extension", icon: "Function"}
// EXIST: { lookupString: "extension", tailText: "() for String in Test.Companion (<root>)", itemText: "extension", icon: "Function"}
