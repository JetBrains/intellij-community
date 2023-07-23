// "Opt in for 'NestedMarker' on 'main'" "true"

class TopClass {
    @RequiresOptIn
    annotation class NestedMarker
}

@TopClass.NestedMarker
class Main

fun main(){
    Main<caret>()
}