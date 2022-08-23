// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// IS_APPLICABLE: false
// WITH_STDLIB
fun test() {
    val x =
        """
                \a
                \b
            """.<caret>trimMargin("\\")
}