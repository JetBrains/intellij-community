// LANGUAGE_VERSION: 1.6

fun t1() : Int{
  return 0
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t1a() : Int {
  <error descr="[RETURN_TYPE_MISMATCH] This function must return a value of type Int">return</error>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t1b() : Int {
  return 1
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t1c() : Int {
  return 1
  <error descr="[RETURN_TYPE_MISMATCH] This function must return a value of type Int"><warning descr="[UNREACHABLE_CODE] Unreachable code">return</warning></error>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t2() : Int {
  if (1 > 2)
    return 1
  else return 1
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t2a() : Int {
  if (1 > 2) {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
  } else { return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t3() : Any {
  if (1 > 2)
    return 2
  else return ""
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t4(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning> : Boolean) : Int {
  do {
    return 1
  }
  while (<warning descr="[UNREACHABLE_CODE] Unreachable code">a</warning>)
  <warning descr="[UNREACHABLE_CODE] Unreachable code">1</warning>
}

fun t4break(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning> : Boolean) : Int {
  do {
    break
  }
  while (<warning descr="[UNREACHABLE_CODE] Unreachable code">a</warning>)
  return 1
}

fun t5() : Int {
  do {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  while (<warning descr="[NON_TRIVIAL_BOOLEAN_CONSTANT] Compiler won't reduce this expression to false in future. Please replace it with a boolean literal. See https://youtrack.jetbrains.com/issue/KT-39883 for details"><warning descr="[UNREACHABLE_CODE] Unreachable code">1 > 2</warning></warning>)
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
}

fun t6() : Int {
  while (<warning descr="[NON_TRIVIAL_BOOLEAN_CONSTANT] Compiler won't reduce this expression to false in future. Please replace it with a boolean literal. See https://youtrack.jetbrains.com/issue/KT-39883 for details">1 > 2</warning>) {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  return 1
}

fun t6break() : Int {
  while (<warning descr="[NON_TRIVIAL_BOOLEAN_CONSTANT] Compiler won't reduce this expression to false in future. Please replace it with a boolean literal. See https://youtrack.jetbrains.com/issue/KT-39883 for details">1 > 2</warning>) {
    break
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  return 1
}

fun t7(b : Int) : Int {
  for (i in 1..b) {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  return 1
}

fun t7break(b : Int) : Int {
  for (i in 1..b) {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  return 1
}

fun t7() : Int {
  try {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  catch (<error descr="[TYPE_MISMATCH] Type mismatch: inferred type is Any but Throwable was expected">e : Any</error>) {
    <warning descr="[UNUSED_EXPRESSION] The expression is unused">2</warning>
  }
  return 1 // this is OK, like in Java
}

fun t8() : Int {
  try {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  catch (<error descr="[TYPE_MISMATCH] Type mismatch: inferred type is Any but Throwable was expected">e : Any</error>) {
    return 1
    <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
  }
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
}

fun blockAndAndMismatch() : Boolean {
  (return true) <warning descr="[UNREACHABLE_CODE] Unreachable code">|| (return false)</warning>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return true</warning>
}

fun tf() : Int {
  try {<warning descr="[UNREACHABLE_CODE] Unreachable code">return</warning> 1} finally{return 1}
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
}

fun failtest(<warning descr="[UNUSED_PARAMETER] Parameter 'a' is never used">a</warning> : Int) : Int {
  if (fail() <warning descr="[UNREACHABLE_CODE] Unreachable code">|| true</warning>) <warning descr="[UNREACHABLE_CODE] Unreachable code">{

  }</warning>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">return 1</warning>
}

fun foo(a : Nothing) : Unit {
  <warning descr="[UNUSED_EXPRESSION] The expression is unused">1</warning>
  <warning descr="[UNUSED_EXPRESSION] The expression is unused">a</warning>
  <warning descr="[UNREACHABLE_CODE] Unreachable code">2</warning>
}

fun fail() : Nothing {
  throw java.lang.RuntimeException()
}

fun nullIsNotNothing() : Unit {
    val x : Int? = 1
    if (x != null) {
         return
    }
    fail()
}