// PROBLEM: none
import java.io.File

fun test(file: File) {
    with(file) {
        val parentFile = <caret>getParentFile()
    }
}