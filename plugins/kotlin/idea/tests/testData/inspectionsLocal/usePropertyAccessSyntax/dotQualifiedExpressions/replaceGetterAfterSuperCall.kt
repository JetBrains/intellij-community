// WITH_STDLIB
// FIX: Use property access syntax
// IGNORE_K1
// PROBLEM: "Use of getter method instead of property access syntax"
import java.io.File

class MyFile : File("file") {
    override fun getCanonicalFile(): File {
        return super.<caret>getCanonicalFile()
    }
}
