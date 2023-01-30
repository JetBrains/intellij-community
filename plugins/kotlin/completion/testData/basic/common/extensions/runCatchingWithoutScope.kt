fun test() {
    runCatchin<caret>
}

// WITH_ORDER
// EXIST: { "lookupString":"runCatching", "tailText":" {...} (block: () -> R) (kotlin)" }
// EXIST: { "lookupString":"runCatching", "tailText":" {...} (block: T.() -> R) for T in kotlin" }
