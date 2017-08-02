class StringEquality {
  int equalsOperator(String param) {
    <caret>if (param == "a") {
      return 1;
    } else if (param == "b") {
      return 2;
    } else {
      return 3;
    }
  }
}