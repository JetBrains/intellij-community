// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class Shadow {
    fun shade() {}
}

fun <X> Shadow.shade() {}

fun context() {
    Shadow().sha<caret>
}

// EXIST: { lookupString: "shade", itemText: "shade", tailText: "()", typeText: "Unit", icon: "Method"}
// EXIST: { lookupString: "shade", itemText: "shade", tailText: "() for Shadow in <root>", typeText: "Unit", icon: "Function"}
// NOTHING_ELSE