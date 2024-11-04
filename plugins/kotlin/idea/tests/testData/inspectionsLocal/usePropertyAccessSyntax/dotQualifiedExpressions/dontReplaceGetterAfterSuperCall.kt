// WITH_STDLIB
// PROBLEM: none
import java.io.File

class MyFile : File("file") {
    override fun getCanonicalFile(): File {
        return super.<caret>getCanonicalFile()
    }
}
