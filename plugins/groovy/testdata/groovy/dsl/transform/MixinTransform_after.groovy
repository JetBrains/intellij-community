@Category(Vehicle)
class DivingAbility {
  def dive() { "I'm the ${name} and I dive!" }
}

@Category(Vehicle) class FlyingAbility {
    def fly() { "I'm the ${name} and I fly!" }
}


interface Vehicle {
  String getName()
}

@Mixin([DivingAbility, FlyingAbility])
class Submarine implements Vehicle {
  String getName() { "Yellow Submarine" }
}

def sub = new Submarine()
sub.dive()<caret>