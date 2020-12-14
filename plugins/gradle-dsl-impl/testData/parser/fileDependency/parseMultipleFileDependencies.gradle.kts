dependencies {
  compile(files("lib1.jar", "lib2.jar"))
  compile(files("lib3.jar"))
  compile(files("lib4.jar", "lib5.jar", "lib6.jar"))
  implementation(files("lib7.jar", "lib8.jar", "lib9.jar"))
}
