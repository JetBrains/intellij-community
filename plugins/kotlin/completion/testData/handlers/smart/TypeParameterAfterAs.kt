package ppp

fun <T> foo(o: Any): T {
    return o as <caret>
}

// AUTOCOMPLETE_SETTING: true