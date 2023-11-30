/* TODO: this test fails for K2 because shadowing isn't supported yet */
import java.io.File

fun File.foo(absolutePath: String?) {
    <caret>
}

// IGNORE_K2
// EXIST: getAbsolutePath
// ABSENT: { itemText: "absolutePath", typeText: "String" }
