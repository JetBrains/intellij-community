dependencies {
  compile(files("lib1.jar", "lib3.jar"))
  compile(files("lib3.jar"))
  compile(files("lib4.jar"))
  compile(files("lib5.aar", "lib6.jar"))
}
