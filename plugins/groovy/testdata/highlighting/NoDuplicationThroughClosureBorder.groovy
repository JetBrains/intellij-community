class Bar {
  def abc
  def foo() {
    def abc
    def x = {
      def abc
      def owner
      println "hello"
    }
    println x
  }
}