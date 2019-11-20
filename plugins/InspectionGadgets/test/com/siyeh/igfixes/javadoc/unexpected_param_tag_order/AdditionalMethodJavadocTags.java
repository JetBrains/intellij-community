class AdditionalMethodJavadocTags {
  /*<caret>*
   * @param a description for parameter a
   * @param nonExistingParameter random description
   * @param b description for parameter b
   * @param c description for parameter c
   * @param <X> description for type parameter X
   * @param <Y> description for type parameter Y
   * @param <Z> description for type parameter Z
   * @param <NON_EXISTING_TYPE_PARAMETER> random description
   * @return always null
   * @throws an exception
   * @see wiki
   * @since 1.0
   * @serial
   * @deprecated
   */
  static <X, Y, Z> Z foo(X a, Y b, Z c) throws Exception {return null;}
}