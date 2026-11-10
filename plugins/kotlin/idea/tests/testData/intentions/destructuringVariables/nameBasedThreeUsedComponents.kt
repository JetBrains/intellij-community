// HIGHLIGHT: INFORMATION
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

fun myFun(element: Options): State {
    val options<caret> = element
    return State().apply {
        autoscrollToSource = options.autoscrollToSource
        openDirectoriesWithSingleClick = options.openDirectoriesWithSingleClick
        autoscrollFromSource = options.autoscrollFromSource
    }
}

data class Options(
    val autoscrollToSource: Boolean,
    val openDirectoriesWithSingleClick: Boolean,
    val autoscrollFromSource: Boolean,
)

class State {
    var autoscrollToSource: Boolean = false
    var openDirectoriesWithSingleClick: Boolean = false
    var autoscrollFromSource: Boolean = false
}
