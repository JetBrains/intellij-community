buildscript {
  var hello by extra("boo")
  dependencies {
    classpath("com.android.tools.build:gradle:$hello")
  }
}
