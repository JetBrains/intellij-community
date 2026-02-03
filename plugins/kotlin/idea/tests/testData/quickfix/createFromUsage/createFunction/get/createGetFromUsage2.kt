// "Create extension function 'Any.get'" "true"
// WITH_STDLIB

fun x (y: Any) {
    val z: Any = y<caret>[""]
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createCallable.CreateExtensionCallableFromUsageFix