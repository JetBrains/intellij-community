val x1 = foo?.bar()
            .baz()
            .quux()

val x2 = foo?.bar()
            ?.baz()
            .quux()

val x3 = foo?.bar()
            ?.baz()
            ?.quux()

val x4 = foo.bar()
            ?.baz()
            ?.quux()

val x5 = foo.bar()
            .baz()
            ?.quux()

val x6 = foo()?.bar()
              .baz()
              .quux()

val x7 = foo()?.bar()
              ?.baz()
              .quux()

val x8 = foo()?.bar()
              ?.baz()
              ?.quux()

val x9 = foo().bar()
              ?.baz()
              ?.quux()

val x10 = foo().bar()
               .baz()
               ?.quux()

val x11 = ((foo()?.bar()))?.baz()
                          ?.quux()

val x12 = ((foo()?.bar())).baz()
                          ?.quux()

val x13 = ((foo().bar())).baz()
                         ?.quux()

val x14 = (foo()?.bar()
                ?.baz())?.quux()

val x15 = (foo()?.bar()
                .baz())?.quux()

val x16 = (foo().bar()
                ?.baz())?.quux()

val x17 = (foo())?.bar()
                 ?.baz()
                 ?.quux()

val x18 = (foo()).bar()
                 ?.baz()
                 ?.quux()

val x19 = (foo())?.bar()
                 .baz()
                 ?.quux()

val x20 = (foo()).bar()
                 .baz()
                 ?.quux()

val x21 = foo!!.bar()
               ?.baz()!!
               .quux()!!

val x22 = foo!!.bar()
               ?.baz()!!
               .quux()!!

val x23 = foo!!!!!!!!.bar()
                     ?.baz()!!
                     .quux()!!

val x24 = a()!!!!.a()
                 ?.a()

val x25 = a()?.a()

val x26 = (a())?.a()

val x27 = (a())?.a()
               ?.a()

val x28 = (a()).a()
               ?.a()

val x29 = (a()?.a())?.a()

val x30 = (a().a())?.a()

val x31 = (a()?.a())?.a()
                    ?.a()

val x32 = (a()?.a()).a()
                    ?.a()

val x33 = (a().a()).a()
                   ?.a()

val x34 = (a()?.a()
              ?.a())?.a()
                    ?.a()

val x35 = (a()?.a()
              ?.a())?.a()

val x36 = foo!!.foo?.baz()!!
                   .quux()!!.foo?.foo?.foo?.baz()?.foo?.baz()
                   ?.baz()

val y = xyzzy(
    foo?.bar()
       ?.baz()
       ?.quux()
)

fun foo() {
    foo?.bar()
       ?.baz()
       ?.quux()

    foo.bar()
       ?.baz()
       ?.quux()

    foo.bar()
       .baz()
       ?.quux()

    z = foo?.bar()
           ?.baz()
           ?.quux()

    z = foo.bar()
           ?.baz()
           ?.quux()

    z = foo.bar()
           .baz()
           ?.quux()

    z += foo?.bar()
            ?.baz()
            ?.quux()

    z += foo.bar()
            ?.baz()
            ?.quux()

    z += foo.bar()
            .baz()
            ?.quux()

    return foo?.bar()
              ?.baz()
              ?.quux()
}

override fun foo(bar: baz) {
    outter?.call {
        val inner = a?.a(it)
        val xyz = col?.map { b -> b.baz(inner) }
                     ?.map { c ->
                         inner?.foo(c)
                              ?.bar(c)
                     }
        inner?.foo(xyz)
             ?.bar()
    }
}

// SET_INT: METHOD_CALL_CHAIN_WRAP = 2
// SET_FALSE: WRAP_FIRST_METHOD_IN_CALL_CHAIN
// SET_TRUE: ALIGN_MULTILINE_CHAINED_METHODS