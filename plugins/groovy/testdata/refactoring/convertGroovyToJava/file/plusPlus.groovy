class PlusPlus {
  public static void main(String[] args) {
    def i = 4
    i++
    assert i == 5
    def a = ++i + i++
    assert i == 7
    assert a == 12
  }
}