interface A {
  def a(int x, double y)

  def b(int x)
}
def x = [
        a: {int x, double y ->
        },
        b: {int x ->
        }
] as A