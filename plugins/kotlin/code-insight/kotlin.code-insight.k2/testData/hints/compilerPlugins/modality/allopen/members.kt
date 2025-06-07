@Open
interface Super

/*<# open #>*/class NoModifiers : Super {
    /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

interface Interface : Super {
    val property = ""
    internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    internal val internalPropetyWithAnnotationOtherLine = ""

    var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    fun function(): String = ""
    internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

abstract class Abstract : Super {
     /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

object Object : Super {
   val property = ""
    internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    internal val internalPropetyWithAnnotationOtherLine = ""

    var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    fun function(): String = ""
    internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

final class Final : Super {
     /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

sealed class Sealed : Super {
    /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

annotation class Annotation : Super {
     val property = ""
    internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    internal val internalPropetyWithAnnotationOtherLine = ""

    var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    fun function(): String = ""
    internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

enum class Enum : Super {
    X, Y, Z

    val property = ""
    internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    internal val internalPropetyWithAnnotationOtherLine = ""

    var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    fun function(): String = ""
    internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}



inline class Inline(val value: Int) : Super {
  /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

@JvmInline
value class Value(val value: Int) : Super {
   /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}

@Deprecated("")
internal abstract sealed class AlotOfModifiersImplicit : Super {
    /*<# open #>*/val property = ""
    /*<# open #>*/internal val internalProperty = ""
    final val finalProperty= ""
    open val openProperty = ""
    abstract val abstractProperty: String

    @Deprecated("") /*<# open #>*/internal val internalPropetyWithAnnotationSameLine = ""

    @Deprecated("")
    /*<# open #>*/internal val internalPropetyWithAnnotationOtherLine = ""

    /*<# open #>*/var propertyWithAccessors: String
        get() = "El Psy Kongroo"
        set(value) { println("El Psy Kongroo") }


    /*<# open #>*/fun function(): String = ""
    /*<# open #>*/internal fun internalFunction(): String = ""
    final fun finalFunction(): String = ""
    open fun openFunction(): String = ""
    abstract fun abstractFunction(): String

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationSameLine(): String = ""

    @Deprecated("")
    /*<# open #>*/internal fun internalFunctionWithAnnotationOtherLine(): String = ""

    constructor(x: Int)

    constructor(): this(1)
}