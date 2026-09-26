import java.*
import java.util.*
import utils.*

import java.io.PrintStream
import java.lang.Comparable as Com

fun <T> checkSubtype(t: T) = t

val l : MutableList<in Int> = ArrayList<Int>()

fun test(l : List<Int>) {
  val x : <error descr="[UNRESOLVED_REFERENCE]">java.List</error>
  val y : List<Int>
  val b : java.lang.Object
  val a : java.util.List<Int>
  val z : <error descr="[UNRESOLVED_REFERENCE]">java.utils.List<Int></error>

  val f : java.io.File? = null

  Collections.<error descr="[FUNCTION_CALL_EXPECTED]">emptyList</error>
  Collections.<error descr="[FUNCTION_CALL_EXPECTED]">emptyList</error><<error descr="[CANNOT_INFER_PARAMETER_TYPE]">Int</error>>
  Collections.emptyList<Int>()
  Collections.<error descr="[NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER]">emptyList</error>()

  checkSubtype<Set<Int>?>(Collections.singleton<Int>(1))
  Collections.singleton<Int>(<error descr="[ARGUMENT_TYPE_MISMATCH]">1.0</error>)

  <error descr="[NO_COMPANION_OBJECT]">List</error><Int>


  val o = "sdf" as Object

  try {
    // ...
  }
  catch(e: Throwable) {
    System.out.println(e.message)
  }

  PrintStream("sdf")

  val c : Com<Int>? = null

  checkSubtype<java.lang.Comparable<Int>?>(c)

//  Collections.sort<Integer>(ArrayList<Integer>())
}
