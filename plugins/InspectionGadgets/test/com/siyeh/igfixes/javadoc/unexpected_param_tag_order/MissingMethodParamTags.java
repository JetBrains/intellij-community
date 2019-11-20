class MissingMethodParamTags {
  /*<caret>*
   * @param a description for parameter a
   * @param c description for parameter c
   * @param <Y> description for type parameter Y
   * @param <Z> description for type parameter Z
   */
  static <X, Y, Z> void foo(X a, Y b, Z c) {}
}