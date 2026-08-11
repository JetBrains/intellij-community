import java.lang.Throwable

fun test(): Throwable {
    return <caret>
}

// ELEMENT: Throwable
// TAIL_TEXT: "(...) (java.lang)"
