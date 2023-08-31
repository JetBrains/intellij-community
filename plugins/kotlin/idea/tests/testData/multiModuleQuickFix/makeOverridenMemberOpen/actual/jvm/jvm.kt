// "Make OClass.overrideMe open" "true"
// KTIJ-26623
// IGNORE_K2
actual open class OClass actual constructor() {
    actual val overrideMe: String = ""
}

class Another: OClass() {
    override<caret> val overrideMe = ""
}