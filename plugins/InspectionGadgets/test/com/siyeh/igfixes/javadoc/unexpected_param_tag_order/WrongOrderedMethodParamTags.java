class WrongOrderedMethodParamTags {
  /*<caret>*
   * @param c description for parameter c
   * @param b description for parameter b
   * @param a description for parameter a
   * @param <Z> description for type parameter Z
   * @param <Y> description for type parameter Y
   * @param <X> description for type parameter X
   */
  static <X, Y, Z> void foo(X a, Y b, Z c) {}
}