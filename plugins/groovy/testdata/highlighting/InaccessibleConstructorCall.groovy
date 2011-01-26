class Base {
  private Base(int i){}
}

class Extension extends Base {
  def Extension() {
    <warning descr="Access to 'Base(int)' exceeds its access rights">super</warning>(1)
  }
}