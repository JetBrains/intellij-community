// WITH_STDLIB
// PROBLEM: none
data class Parent(
    val child: Int?,
)

fun function(parent: Parent?) = if (parent =<caret>= null) {
    "placeholder"
} else {
    parent.child?.toString().orEmpty()
}

// IGNORE_K1