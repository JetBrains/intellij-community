class WrongIndentMultiParam(
    val id: String,
    val name: String,

)

class WrongIndentSingleParam(
    val id: String,

)

class NoBlankLineKeepsColumnZero(
    val id: String,
    val name: String,
)

class NoTrailingCommaWithBlankLineKeepsColumnZero(
    val id: String,
    val name: String,

)

// SET_TRUE: ALLOW_TRAILING_COMMA
// SET_INT: METHOD_PARAMETERS_WRAP = 1
