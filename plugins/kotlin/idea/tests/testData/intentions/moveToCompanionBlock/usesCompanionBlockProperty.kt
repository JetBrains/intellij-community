// COMPILER_ARGUMENTS: -Xcompanion-blocks

class Sample {
    companion {
        var targetProperty = 0
    }

    var <caret>movedProperty = targetProperty
}
