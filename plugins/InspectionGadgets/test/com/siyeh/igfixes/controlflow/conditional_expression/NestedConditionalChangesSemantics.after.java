class NestedConditional {

  private String nullIfEmpty(String str) {
      if (str.isEmpty()) return str == null ? null : null;
      return str == null ? null : str;
  }
}