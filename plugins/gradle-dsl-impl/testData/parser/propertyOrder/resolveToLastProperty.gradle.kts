var var1 = "hello"
var1 = "goodbye"
var var2 = "on"
var2 = "off"
val greeting by extra(var1)
val state by extra(var2)

android {
  signingConfigs {
    create("myConfig") {
      storeFile = file(greeting)
      storePassword = state
    }
  }
}
