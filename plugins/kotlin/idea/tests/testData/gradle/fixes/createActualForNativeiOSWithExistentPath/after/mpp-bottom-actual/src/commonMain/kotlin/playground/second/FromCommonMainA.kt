// "Create actual class for module KT34159-create-actual.mpp-bottom-actual.dummyiOSMain (Native)" "true"
// ACTION: Convert to secondary constructor
// ACTION: Create subclass
// ACTION: Extract 'FromCommonMainAExp' from current file
// ACTION: Rename class to FromCommonMainA
// ACTION: Rename file to FromCommonMainAExp.kt
package playground.second

expect open class FromCommonMainAExp() {
    open fun c(a: Int, b: String)
}

class InheritorInCommonMainExp : FromCommonMainAExp() {
    override fun c(a: Int, b: String) {}
}