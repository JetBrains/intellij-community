class Test {
  def r() {
    if (equals(new Object())) {
      throw new RuntimeException()
    }
    return 239
  }
}