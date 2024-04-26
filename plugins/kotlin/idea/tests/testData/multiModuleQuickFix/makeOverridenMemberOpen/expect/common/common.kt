// "Make OClass.overrideMe open" "true"
// IGNORE_K2
expect open class OClass() {
    val overrideMe: String
}

class Another: OClass() {
    override<caret> val overrideMe = ""
}