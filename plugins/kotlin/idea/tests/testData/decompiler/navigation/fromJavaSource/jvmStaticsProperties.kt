package testData.libraries

class JvmStaticsPropertiesConflictingWithClass {
    object prop {}

    companion object {
        @JvmStatic
        var prop = true
    }

}