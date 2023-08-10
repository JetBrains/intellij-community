// FIR_COMPARISON
// FIR_IDENTICAL
class Foo {
    val x = runCatchin<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"runCatching", "tailText":" {...} (block: Foo.() -> R) for T in kotlin" }
// EXIST: { "lookupString":"runCatching", "tailText":" {...} (block: () -> R) (kotlin)" }
