class X {
  X() {
  }

  private int a;
  int b;

  void setF(int a) {}

  def getK() {2}
}

def x = new X(a: 4 , b: 5, f: 7, <warning descr="Property 'k' does not exist">k</warning>:8)
print x.k;





