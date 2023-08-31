import kotlin.io.DEFAULT_BUFFER_SIZE as BUFSIZE

fun foo() {
    BUF<caret>
}

// IGNORE_K2
// ELEMENT: "BUFSIZE"