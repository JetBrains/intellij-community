// "Import property 'f'" "true"
// ERROR: Cannot access 'f': it is private in file

package my.pack

fun main() {
    <caret>f
}

/* IGNORE_FIR */
