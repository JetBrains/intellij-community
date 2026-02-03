// WITH_STDLIB
// TODO: seems a bug
// AFTER-WARNING: Name shadowed: reader
import java.io.File
import java.io.BufferedReader

fun bar() {}

fun foo(reader: BufferedReader?) {
    <caret>try {
        reader?.readLine()
        bar()
    }
    finally {
        reader?.close()
    }
}