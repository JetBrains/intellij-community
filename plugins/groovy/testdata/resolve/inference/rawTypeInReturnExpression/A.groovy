class MissingInferenceTest {
  Map<String, Map<Integer, List<Date>>> factoryMethod() { [:]}

  def myMethod() {
    Map<String, Map<Integer, List<Date>>> dataTyped = [:]
    def dataInferred = new HashMap<String, Map<Integer, List<Date>>>()
    def dataNotInferred = factoryMethod()

    println dataTyped      ['foo'][5][2].time()
    println dataInferred   ['foo'][5][2].time()
    println dataNotInferred['foo'][5][2].tim<ref>e() // no completion, highlighted as dynamic
  }
}

