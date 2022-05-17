// "Add initializer" "false"
// WITH_STDLIB
// ACTION: Convert to ordinary property
// ACTION: Create test
// ACTION: Make internal
// ACTION: Make private
<caret>val n: Int by lazy { 0 }