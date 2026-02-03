import java.io.InputStream

fun foo(): InputStream {
    return <caret>
}

// EXIST: ByteArrayInputStream
// EXIST: FileInputStream

// IGNORE_K2
