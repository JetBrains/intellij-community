class Foo {


  def bar = { 2}
}

class Bar extends Foo {
  def bar(def it) { 3 }

  public static void main(String[] args) {
    def f = new Bar()

    
    println f.ba<ref>r(3)
  }

}
