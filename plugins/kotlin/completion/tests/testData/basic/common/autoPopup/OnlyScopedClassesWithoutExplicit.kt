package first

fun firstFun() {
  val a = In<caret>
}

// INVOCATION_COUNT: 0
// EXIST: { lookupString:"Int", tailText:" (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// ABSENT: { lookupString:"IntRef", tailText:" (kotlin.internal.Ref)" }
