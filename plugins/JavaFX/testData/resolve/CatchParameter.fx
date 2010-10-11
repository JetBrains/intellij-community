import java.lang.Exception;

try {
  foo()
}
catch (e: FileNotFoundException) {
  print("File not found");
}
catch (e: Exception) {
  <ref>e.printStackTrace()
}

