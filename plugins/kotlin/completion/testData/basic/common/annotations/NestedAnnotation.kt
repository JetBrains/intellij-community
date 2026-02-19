// IGNORE_K1
class KtAnnIsMissing

class KtAnnContainer {
    annotation class KtAnnNested
}

@KtAnn<caret>Container.KtAnnNested
class KtAnnedClass

// EXIST: {"lookupString":"KtAnnNested","tailText":" (KtAnnContainer)","icon":"org/jetbrains/kotlin/idea/icons/annotationKotlin.svg","attributes":"","itemText":"KtAnnNested"}
// EXIST: {"lookupString":"KtAnnContainer","tailText":" (<root>)","icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg","attributes":"","itemText":"KtAnnContainer"}
// NOTHING_ELSE