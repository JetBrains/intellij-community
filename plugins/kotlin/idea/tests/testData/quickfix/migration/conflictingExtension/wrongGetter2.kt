// "Delete redundant extension property" "false"
// ACTION: Convert property to function
// ACTION: Create test
// ACTION: Do not show return expression hints
// ACTION: Remove explicit type specification
import java.io.File

public val File.<caret>parent: File?
    get() = getParentFile()
