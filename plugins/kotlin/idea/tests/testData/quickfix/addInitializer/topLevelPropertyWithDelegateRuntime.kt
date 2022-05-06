// "Add initializer" "false"
// WITH_STDLIB
// ACTION: Convert to ordinary property
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Make internal
// ACTION: Make private
<caret>val n: Int by lazy { 0 }