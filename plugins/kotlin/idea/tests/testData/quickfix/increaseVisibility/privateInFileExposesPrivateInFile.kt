// "Make 'Private' public" "true"

private interface I2 {
    private class Private
    fun <caret>pp() = Private()
}