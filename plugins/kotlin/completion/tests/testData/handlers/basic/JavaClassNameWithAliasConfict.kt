package one

import three.Nest as Egg

class Outer {
    fun t(n: Eg<caret>) {
    }
}

// ELEMENT: Egg
// TAIL_TEXT: " (two)"