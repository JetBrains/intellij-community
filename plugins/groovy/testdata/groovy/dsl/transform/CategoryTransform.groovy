@Category(Vehicle)
class DivingAbility {
  def dive() { "I'm the ${nam<caret>} and I dive!" }
}

interface Vehicle {
  String getName()
}
