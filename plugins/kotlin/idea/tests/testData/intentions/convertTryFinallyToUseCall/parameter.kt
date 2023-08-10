// WITH_STDLIB
// TODO: seems a bug
// AFTER-WARNING: Name shadowed: reader
import java.io.File
import java.io.BufferedReader

fun foo(reader: BufferedReader) {
    try <caret>{
        reader.readLine()
    }
    finally {
        reader.close()
    }
}