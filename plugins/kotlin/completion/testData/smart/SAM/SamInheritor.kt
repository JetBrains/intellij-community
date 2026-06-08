interface Transformer {
    fun transform(input: String): String
}

fun interface UpperCaseTransformer : Transformer {
    fun other() {}
}

var t: Transformer = UpperCaseTransforme<caret>

// EXIST: {"lookupString":"UpperCaseTransformer","itemText":"UpperCaseTransformer","tailText":" {...} (function: (String) -> String) (<root>)","typeText":"UpperCaseTransformer"}

