private boolean function() {
  try {
    if (System.currentTimeMillis() %5) throw new Exception()
    println "foo"
    return true
  } finally {
    println "bar"
  }
}