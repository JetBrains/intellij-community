// INTENTION_CLASS: org.jetbrains.kotlin.android.intention.ImplementParcelableAction
// WITH_STDLIB

class <caret>WithTransient() {
    @Transient var transientText: String = ""
    var text: String = ""
}