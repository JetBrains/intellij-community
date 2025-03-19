import EE.ENUM_ENTRY

enum class EE {
    ENUM_ENTRY
}

var test: Int = 0
    set(parameter: Int) {
        val localVariable = 1
        val r = ::<caret>
    }

// ABSENT: ENUM_ENTRY
// ABSENT: parameter
// ABSENT: localVariable
// ABSENT: field