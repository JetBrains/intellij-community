val dep by extra("a:b:1.0")
val other by extra(dep)
val someOther by extra(other)
dependencies {
  compile(someOther)
}
