class NegatedConditional {
  String text;

  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof NegatedConditional)) return false;
    if (!super.equals(object)) return false;

    final NegatedConditional that = (NegatedConditional)object;

      return text != null ? text.equals(that.text) : that.text == null;

  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (text != null ? text.hashCode() : 0);
    return result;
  }
}