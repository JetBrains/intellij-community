// PROBLEM: none
import java.io.File

fun test(file: File) {
    with(file) {
        val parentFile = File("path")
        <caret>getParentFile()
    }
}