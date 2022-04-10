// FIR_IDENTICAL

fun Any.<warning descr="[EXTENSION_SHADOWED_BY_MEMBER] Extension is shadowed by a member: public open operator fun equals(other: Any?): Boolean">equals</warning>(<warning descr="[UNUSED_PARAMETER] Parameter 'other' is never used">other</warning> : Any?) : Boolean = true

fun main(<warning descr="[UNUSED_PARAMETER] Parameter 'args' is never used">args</warning>: Array<String>) {

    val command : Any = 1

    command<warning descr="[SAFE_CALL_WILL_CHANGE_NULLABILITY] Safe call on a non-null receiver will have nullable type in future releases
  Right now safe call on non nullable receiver has not null type: `\"hello\"?.length` has type Int
  In future releases all safe calls will have nullable type: `\"hello\"?.length` will have type Int?"><warning descr="[UNNECESSARY_SAFE_CALL] Unnecessary safe call on a non-null receiver of type Any">?.</warning>equals(null)</warning>
    command.equals(null)
}
