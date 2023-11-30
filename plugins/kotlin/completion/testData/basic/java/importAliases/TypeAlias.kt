import kotlin.collections.ArrayList as KotlinArrayList

fun foo(): KotAr<caret>

// IGNORE_K2
// EXIST: { lookupString: "KotlinArrayList", itemText: "KotlinArrayList", tailText: "<E> (kotlin.collections.ArrayList)", typeText: "ArrayList<E>", icon: "org/jetbrains/kotlin/idea/icons/typeAlias.svg"}
