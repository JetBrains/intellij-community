// "Import class 'Comparator'" "true"
import java.util.Collections
import java.util.ArrayList

fun foo() {
    Collections.sort(
            ArrayList<Int>(),
            <caret>Comparator { x: Int, y: Int -> x - y }
    )
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// IGNORE_K2