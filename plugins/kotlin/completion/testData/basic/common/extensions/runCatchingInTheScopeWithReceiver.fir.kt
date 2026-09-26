// FIR_COMPARISON
class Foo {
    val x = runCatchin<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"runCatching", "tailText":" { block: Foo.() -> R } for T in kotlin" }
// EXIST: { "lookupString":"runCatching", "tailText":"(block: Foo.() -> R) for T in kotlin" }
// EXIST: { "lookupString":"runCatching", "tailText":" { block: () -> R } (kotlin)" }
// EXIST: { "lookupString":"runCatching", "tailText":"(block: () -> R) (kotlin)" }
