// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
val int = 1
val double = 1.0
val float = 1.0f
val long = 1L
val bool = true
val char = 'c'
val string = "some string"
val explicit: ExplicitType = someValue

val a = <caret>

// EXIST: { lookupString:"int", typeText:"Int" }
// EXIST: { lookupString:"double", typeText:"Double" }
// EXIST: { lookupString:"float", typeText:"Float" }
// EXIST: { lookupString:"bool", typeText:"Boolean" }
// EXIST: { lookupString:"char", typeText:"Char" }
// EXIST: { lookupString:"string", typeText:"String" }
// EXIST: { lookupString:"explicit", typeText:"ExplicitType" }