// WITH_STDLIB
// TODO: seems a bug
// AFTER-WARNING: Name shadowed: reader
// AFTER-WARNING: Parameter 'args' is never used
import java.io.File

fun main(args: Array<String>) {
    val reader = File("hello-world.txt").bufferedReader()
    try <caret>{
        reader.readLine()
    }
    finally {
        reader.close()
    }
}