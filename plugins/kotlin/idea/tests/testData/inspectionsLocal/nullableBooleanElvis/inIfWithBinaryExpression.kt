// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
fun foo() {
    var a: Int? = null
    var b: Boolean? = null
    fun isOddNumber(n: Int?): Boolean? {
        if (n == null) return null
        if (n < 0) return null
        return n % 2 == 1
    }
    if ((isOddNumber(a) ?: b) <caret>?: false) {

    }
}