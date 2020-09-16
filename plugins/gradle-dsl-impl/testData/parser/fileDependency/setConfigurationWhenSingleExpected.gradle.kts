dependencies {
  "androidTest"(files("libs"))
  api(files("xyz"))
  compile(files("klm"))
  testImplementation(files("a")) {
    // would not currently compile: testing syntax support only
  }
}
