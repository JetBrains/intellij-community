// "Safe delete 'Imported'" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Extract 'ImportedClass' from current file
// ACTION: Rename file to ImportedClass.kt
import ImportedClass as ClassAlias

class <caret>ImportedClass

fun use() {
    ClassAlias().hashCode()
}