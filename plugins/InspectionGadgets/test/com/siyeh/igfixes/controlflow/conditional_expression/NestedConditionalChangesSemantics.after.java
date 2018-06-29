class NestedConditional {

  private String nullIfEmpty(String str) {
      if (str.isEmpty()) return str == null ? null : null;
      else return str == null ? null : str;
  }
}