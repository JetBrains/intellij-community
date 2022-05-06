// "Safe delete 'Imported'" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Extract 'Imported' from current file
// ACTION: Rename file to Imported.kt
import Imported as Alias

object <caret>Imported

fun use() {
    Alias.hashCode()
}