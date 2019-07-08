class C {
  C<caret>(List<? extends Serializable> t) {}
}


def x = new C([2])
def y = new C(['q'])