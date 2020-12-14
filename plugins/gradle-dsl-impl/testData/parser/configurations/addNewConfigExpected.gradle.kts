val var1 by extra(true)
val var2 by extra(false)
configurations {
  create("newConfig")
  create("otherNewConfig") {
    isVisible = var1
  }
}
