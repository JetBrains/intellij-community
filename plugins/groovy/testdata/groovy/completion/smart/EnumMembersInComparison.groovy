enum ItemStatus {
 ORDERED, IN_STOCK, NOWHERE
}

class Item {
  ItemStatus status

  def test() {
    if (status == <caret>) {}
   }
}