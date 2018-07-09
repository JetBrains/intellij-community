class NestedConditional {

  private String nullIfEmpty(String str) {
      if (str == null) return null;
      else return str.isEmpty() ? null : str;
  }
}