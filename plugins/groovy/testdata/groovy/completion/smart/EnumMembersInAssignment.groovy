public enum ItemStatus {
 ORDERED, IN_STOCK, NOWHERE
}

class Item {
  ItemStatus status
 
  static constraints = {
    status nullable: false
   }

  def test() {
    status = IS<caret>
   }
}