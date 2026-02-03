fun test() {
    runCatchin<caret>
}

// IGNORE_K1
// WITH_ORDER
// EXIST: { "lookupString":"runCatching", "tailText":" { block: () -> R } (kotlin)" }
// EXIST: { "lookupString":"runCatching", "tailText":"(block: () -> R) (kotlin)" }
// ABSENT: { "lookupString":"runCatching", "tailText":" { block: T.() -> R } for T in kotlin" }
// ABSENT: { "lookupString":"runCatching", "tailText":"(block: T.() -> R) for T in kotlin" }
