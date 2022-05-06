// "Delete redundant extension property" "false"
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Remove explicit type specification
import java.io.File

var File.<caret>name: String
    get() = getName()
    set(value) {}
