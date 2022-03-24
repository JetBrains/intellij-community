// WITH_STDLIB
// TODO: seems a bug
// AFTER-WARNING: Name shadowed: writer
// AFTER-WARNING: Parameter 'args' is never used
import java.io.File

fun main(args: Array<String>) {
    val writer = File("hello-world.txt").bufferedWriter()
    try <caret>{
        writer.write("123")
        writer.newLine()
        writer.write("456")
    }
    finally {
        writer.close()
    }
}