public class Usage {
  int usage(Test[] tests) {
    int sum = 0;
    int i = 0;
    while (i < tests.length) {
        final Test test = tests[i++]
        sum += test.method(test.field);
    }

    List list = Arrays.asList(tests);
    i = 0;
    while (i < list.size()) {
        final Test test = (Test) list.get(i++)
        sum += test.method(test.field);
    }
  }
}