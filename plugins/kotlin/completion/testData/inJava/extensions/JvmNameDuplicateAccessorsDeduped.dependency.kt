// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package a

class Target

@get:JvmName("xLongPrefixGetter")
@set:JvmName("xLongPrefixSetter")
var Target.value: String
    get() = ""
    set(v) {}
