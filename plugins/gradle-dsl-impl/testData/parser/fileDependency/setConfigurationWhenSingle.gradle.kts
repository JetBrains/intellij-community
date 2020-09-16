dependencies {
  test(files("libs"))
  compile(files("xyz"))
  api(files("klm"))
  testCompile(files("a")) {
    // would not currently compile: testing syntax support only
  }
}
