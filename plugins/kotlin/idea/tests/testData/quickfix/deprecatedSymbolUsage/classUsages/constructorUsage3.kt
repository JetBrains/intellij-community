// "Replace with 'NewClass'" "true"
package ppp

@Deprecated("", ReplaceWith("NewClass"))
class OldClass(p: Int)

class NewClass(p: Int)

fun foo() {
    ppp.<caret>OldClass(1)
}
