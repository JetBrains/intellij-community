class WrongOrderedMethodParamTags {
  /**
   * @param a description for parameter a
   * @param b description for parameter b
   * @param c description for parameter c
   * @param <X> description for type parameter X
   * @param <Y> description for type parameter Y
   * @param <Z> description for type parameter Z
   */
  static <X, Y, Z> void foo(X a, Y b, Z c) {}
}