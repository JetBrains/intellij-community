// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class <warning descr="Class A is unused">A</warning> {}

interface <warning descr="Interface I is unused">I</warning> {}

trait <warning descr="Trait T is unused">T</warning> {}

enum <warning descr="Enum E is unused">E</warning>{}

<error descr="Records are available in Groovy 4.0.0-beta-2 or later">record</error> <warning descr="Record R is unused">R</warning>(String x){}

static <<warning descr="Type parameter P is unused">P</warning>> void <warning descr="Method method is unused">method</warning>() {}