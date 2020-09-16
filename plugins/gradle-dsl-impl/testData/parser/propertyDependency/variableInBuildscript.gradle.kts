buildscript {
  val kotlin by extra("2.0")

  dependencies {
    compile("hello:kotlin:${kotlin}")
  }
}
