import java.lang.IllegalArgumentException

fun some(e: IllegalArgumentException<caret>) {
}

// INVOCATION_COUNT: 2
// WITH_ORDER
// EXIST: { lookupString:"IllegalArgumentException", tailText:" (java.lang)", icon: "fileTypes/javaClass.svg"}
// EXIST: { lookupString:"IllegalArgumentException", tailText: " (kotlin)", typeText:"IllegalArgumentException", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}
