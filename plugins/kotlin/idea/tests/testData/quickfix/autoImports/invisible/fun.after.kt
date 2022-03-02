// "Import" "true"
// ERROR: Cannot access 'f': it is private in file

package my.pack

import simple.f

fun main() {
    <caret>f()
}
