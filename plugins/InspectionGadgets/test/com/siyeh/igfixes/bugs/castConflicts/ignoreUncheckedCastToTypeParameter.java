class Test {
  public static <E> E getValue(final Object param, final Class<E> clazz) {
    if(param instanceof String && String.class.equals(clazz)) {
      return (E)<caret>param;
    }
    return null;
  }
}