public enum ItemStatus {
 ORDERED, IN_STOCK, NOWHERE;

  def foo() {
    ItemStatus status = <caret>
  }
}