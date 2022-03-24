// "Import" "true"
// ERROR: Cannot access 'F': it is private in file

package my.pack

import simple.F

fun main() {
    val f: F<caret>
}