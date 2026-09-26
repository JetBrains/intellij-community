fun test(): Runnable {
    return <caret>
}

// EXIST: {"lookupString":"Runnable","itemText":"Runnable","tailText":" {...} (function: () -> Unit) (java.lang)","typeText":"Runnable"}
