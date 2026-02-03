public class Usage {
  int usage(Test[] tests) {
    int sum = 0;
    int i = 0;
    while (i < tests.length) {
      sum += tests[i++].method();
    }

    List list = Arrays.asList(tests);
    i = 0;
    while (i < list.size()) {
      sum += ((Test) list.get(i++)).method();
    }
  }
}