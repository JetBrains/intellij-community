fun test() {
    runCatchin<caret>
}


// WITH_ORDER
// EXIST: { "lookupString":"runCatching", "tailText":" { block: () -> R } (kotlin)" }
// EXIST: { "lookupString":"runCatching", "tailText":"(block: () -> R) (kotlin)" }
// ABSENT: { "lookupString":"runCatching", "tailText":" { block: T.() -> R } for T in kotlin" }
// ABSENT: { "lookupString":"runCatching", "tailText":"(block: T.() -> R) for T in kotlin" }
