@Category(Vehicle)
class DivingAbility {
  def dive() { "I'm the ${name<caret>} and I dive!" }
}

interface Vehicle {
  String getName()
}
