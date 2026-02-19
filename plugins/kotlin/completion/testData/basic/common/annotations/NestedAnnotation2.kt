class KtAnnContainer {
    annotation class KtAnnNested
}

@KtAnnContainer.KtAnn<caret>Nested
class KtAnnedClass

// EXIST: {"lookupString":"KtAnnNested","tailText":" (KtAnnContainer)","icon":"org/jetbrains/kotlin/idea/icons/annotationKotlin.svg","attributes":"","itemText":"KtAnnNested"}