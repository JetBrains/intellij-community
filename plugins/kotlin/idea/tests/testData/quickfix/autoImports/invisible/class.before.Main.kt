// "Import class 'F'" "true"
// ERROR: Cannot access 'F': it is private in file

package my.pack

fun main() {
    val f: F<caret>
}
/* IGNORE_FIR */
