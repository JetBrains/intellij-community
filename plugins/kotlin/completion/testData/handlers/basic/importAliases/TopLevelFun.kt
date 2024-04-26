import kotlin.error as veryBad

fun foo() {
    v<caret>
}

// IGNORE_K2
// ELEMENT: "veryBad"