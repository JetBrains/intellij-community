import kotlin.io.DEFAULT_BUFFER_SIZE as BUFSIZE

fun foo() {
    BUF<caret>
}

// IGNORE_K2
// EXIST: { lookupString: "BUFSIZE", itemText: "BUFSIZE", tailText: " (kotlin.io.DEFAULT_BUFFER_SIZE)", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
