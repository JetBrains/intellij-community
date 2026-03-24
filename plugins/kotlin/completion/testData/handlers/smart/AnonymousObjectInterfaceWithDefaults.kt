interface WithDefaults {
    fun required(): String
    fun optional(): String = "default"
}

val a : WithDefaults = <caret>

//ELEMENT: object
