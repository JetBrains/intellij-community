class QualifyWithThis1 {
  String[] values = new String[10];
  void foo() {
    int size = values.length;
    String[] values = new String[10]);  // Let's hide filed "values" with local variable

      for (String value : this.values) {
      }
  }
}