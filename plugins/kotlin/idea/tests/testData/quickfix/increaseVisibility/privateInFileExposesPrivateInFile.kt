// "Make 'Private' public" "true"

private interface I2 {
    private class Private
    fun <caret>pp() = Private()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix