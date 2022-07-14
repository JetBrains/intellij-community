// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@Suppress("INTERFACE_WITH_SUPERCLASS")
interface B : F

@Suppress("INTERFACE_WITH_SUPERCLASS")
interface C : F

open class D : C, A()

interface E : D