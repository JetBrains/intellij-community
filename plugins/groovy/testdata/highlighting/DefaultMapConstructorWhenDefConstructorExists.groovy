class X {
  X() {
  }

  private int a;
  int b;

  void setF(int a) {}

  def getK() {}
}

def x = new X(a: 4 , b: 5, f: 7, <error descr="Property 'k' does not exist">k</error>:8)
print x.k;





