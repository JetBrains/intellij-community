 var x : Int = 1 + <error descr="[UNINITIALIZED_VARIABLE]">x</error>
   get() : Int = 1
   set(value : <error descr="[WRONG_SETTER_PARAMETER_TYPE]">Long</error>) {
      field = value.toInt()
      field = <error descr="[TYPE_MISMATCH]">1.toLong()</error>
    }

 val xx : Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">1 + x</error>
   get() : Int = 1
   <error descr="[VAL_WITH_SETTER]">set(<warning descr="[UNUSED_PARAMETER]">value</warning> : <error descr="[WRONG_SETTER_PARAMETER_TYPE]">Long</error>) {}</error>

  val p : Int = <error descr="[PROPERTY_INITIALIZER_NO_BACKING_FIELD]">1</error>
    get() = 1

class Test() {
    var a : Int = 111
    var b : Int get() = a; set(x) { a = x }

   init {

   }
   fun f() {

   }
}
