// "Make OClass.overrideMe open" "true"
// KTIJ-26623
// ERROR: Actual class 'OClass': actual class and its non-final expect class must declare exactly the same non-private members. The following non-private members in actual class are mismatched:<br><br>property 'overrideMe': the modality of this member must be the same in the expect class and the actual class. This error happens because the expect class 'OClass' is non-final<br><br>This error happens because the expect class 'OClass' is non-final. Also see https://youtrack.jetbrains.com/issue/KT-22841 for more details
// ERROR: property 'overrideMe': the modality of this member must be the same in the expect class and the actual class. This error happens because the expect class 'OClass' is non-final. Also see https://youtrack.jetbrains.com/issue/KT-22841 for more details

actual open class OClass actual constructor() {
    actual val overrideMe: String = ""
}

class Another: OClass() {
    override<caret> val overrideMe = ""
}