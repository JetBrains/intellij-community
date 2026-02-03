// LANGUAGE_VERSION: 2.0
// PROBLEM: none

@RequiresOptIn
annotation class MyOptIn

class Ignored(
    <caret>@OptIn(MyOptIn::class) val bar: String = ""
)
