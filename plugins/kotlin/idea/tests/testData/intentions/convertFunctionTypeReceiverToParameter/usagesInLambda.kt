fun foo(): <caret>Int.() -> String = {
    toString() + hashCode() + this.hashCode() + bar(this)
}

fun bar(i: Int) = i + 1