
fun test() {
    someUnknownPrefix.val<caret>
}

// EXIST: .val
// NUMBER: 1
// NOTHING_ELSE
// INVOCATION_COUNT: 1
// WITH_LIVE_TEMPLATES