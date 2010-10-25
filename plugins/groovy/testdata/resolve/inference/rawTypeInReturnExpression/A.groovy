class MissingInferenceTest {
  Map<String, Map<Integer, List<String>>> factoryMethod() { new HashMap()}

  def myMethod() {
    Map<String, Map<Integer, List<String>>> dataTyped = new HashMap()
    def dataInferred = new HashMap<String, Map<Integer, List<String>>>()
    def dataNotInferred = factoryMethod()

    println dataTyped      ['foo'][5][2].charAt(2)
    println dataInferred   ['foo'][5][2].charAt(2)
    println dataNotInferred['foo'][5][2].c<ref>harAt(2) // no completion, highlighted as dynamic
  }
}

