class MultipleComments {

  String x() {
    return new <caret>StringBuilder().append("one\n")
      .append("two" //simple end comment
      ).toString() //NON-NLS

  }
}