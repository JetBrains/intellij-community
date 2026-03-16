// PROBLEM: 'main()' should return Unit
// FIX: Add explicit Unit return type
// DISABLE_ERRORS
fun <caret>main(args: Array<String>) = 1

// K1 has different wording
// IGNORE_K1