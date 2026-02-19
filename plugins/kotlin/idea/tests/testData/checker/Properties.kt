 var x : Int = 1 + <error descr="[UNINITIALIZED_VARIABLE] Variable 'x' must be initialized">x</error>
   get() : Int = 1
   set(value : <error descr="[WRONG_SETTER_PARAMETER_TYPE] Setter parameter type must be equal to the type of the property, i.e. 'Int'">Long</error>) {
      field = value.toInt()
      field = <error descr="[TYPE_MISMATCH] Type mismatch: inferred type is Long but Int was expected">1.toLong()</error>
    }

 val xx : Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD] Initializer is not allowed here because this property has no backing field">1 + x</error>
   get() : Int = 1
   <error descr="[VAL_WITH_SETTER] A 'val'-property cannot have a setter">set(<warning descr="[UNUSED_PARAMETER] Parameter 'value' is never used">value</warning> : <error descr="[WRONG_SETTER_PARAMETER_TYPE] Setter parameter type must be equal to the type of the property, i.e. 'Int'">Long</error>) {}</error>

  val p : Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD] Initializer is not allowed here because this property has no backing field">1</error>
    get() = 1

class Test() {
    var a : Int = 111
    var b : Int get() = a; set(x) { a = x }

   init {

   }
   fun f() {

   }
}
