package test

// a class similar to androidx.compose.ui.Modifier, see KTIJ-28887
interface Modifier {
    fun member(): Modifier

    companion object : Modifier {
        override fun member(): Modifier = this
    }
}

fun action(modifier: Modifier) {}

fun test() {
    action(modifier = Mod<caret>)
}

// IGNORE_K2
// ORDER: Modifier
// ORDER: member
