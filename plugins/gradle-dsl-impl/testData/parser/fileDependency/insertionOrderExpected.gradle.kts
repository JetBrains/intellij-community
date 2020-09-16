dependencies {
  "feature"(files("b.jar"))
  api(files("a.jar"))
  implementation("androidx.constraintlayout:constraintlayout:1.1.3")
  testCompile(files("c.jar"))
}
