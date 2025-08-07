// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB
import java.io.File

fun main(args: Array<String>) {
    val reader = File("hello-world.txt").bufferedReader()
    try<caret> {
        reader.readLine()
    }
    finally {
        reader.readLine()
        reader.close()
    }
}