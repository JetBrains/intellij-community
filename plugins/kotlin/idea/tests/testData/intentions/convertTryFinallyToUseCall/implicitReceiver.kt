// WITH_STDLIB
import java.io.File
import java.io.BufferedReader

fun BufferedReader.foo() {
    try <caret>{
        readLine()
    }
    finally {
        close()
    }
}