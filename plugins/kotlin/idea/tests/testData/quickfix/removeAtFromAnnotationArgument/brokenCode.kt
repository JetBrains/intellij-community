// "Remove @ from annotation argument" "true"
// DISABLE_ERRORS
@Suppress({ <caret>@x
y() }) fun test() {}
// IGNORE_K2
// KT-72831
// KTIJ-31896