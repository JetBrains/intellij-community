fun test() {
    runCatchin<caret>
}

// IGNORE_K1
// EXIST: { "lookupString":"runCatching", "tailText":" {...} (block: () -> R) (kotlin)" }
// ABSENT: { "lookupString":"runCatching", "tailText":" {...} (block: T.() -> R) for T in kotlin" }
