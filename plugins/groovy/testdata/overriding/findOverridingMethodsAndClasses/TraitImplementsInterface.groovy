public interface ShowSiblings {
  void doSmth();
}
// ******************************
trait A implements ShowSiblings {
  public void doSmth() {
    println("traitA");
  }
}

trait B implements ShowSiblings {
  public void doSmth() {
    println("traitB");
  }
}
// ******************************
class C implements ShowSiblings {
  public void doSmth() {
    println("classC");
  }
}

class D implements ShowSiblings {
  public void doSmth() {
    println("classD");
  }
}