// WITH_STDLIB
import java.io.File
import java.io.BufferedReader

fun BufferedReader.foo() {
    <caret>try {
        this.readLine()
    }
    finally {
        this.close()
    }
}