class AdditionalData {
    public val timeRange: String = "str"
}

fun curveSet(
    log: String, timeRange: String,
    graphVarId: String, xVariable: String,
    unitSettings: String, rightColor: String
): String {
    TODO()
}

fun main() {
    var log: String = "str"
    var xVariable = "str"
    var unitSettings = "str"
    var rightColor = "str"
    var additionalData = AdditionalData()
    var variableToUseAsAltitude = AdditionalData()

    var a = curveSet(log, additionalData.timeRange, variableToUseAsAltitude.timeRange, xVariable, unitSettings,<caret> rightColor
    )
}