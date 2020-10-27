buildscript {
  dependencies {
    val hello by extra("boo")
    classpath("com.android.tools.build:gradle:$hello")
  }
}
extra["goodbye"] = hello
extra["goodday"] = buildscript.dependencies.extra["hello"]
