import static Sub.*

class Super {
  static void xxfoo() {}
}
class Sub extends Super {}

xxfoo()<caret>
