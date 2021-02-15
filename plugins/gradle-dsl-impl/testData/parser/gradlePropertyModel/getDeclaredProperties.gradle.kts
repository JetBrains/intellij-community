android {
  val outerVar = "Spooky"
  buildTypes {
    getByName("debug") {
      val innerVar = sneaky
      isMinifyEnabled = true
    }
  }
}
val prop1 by extra("property")
val topVar = "value"
