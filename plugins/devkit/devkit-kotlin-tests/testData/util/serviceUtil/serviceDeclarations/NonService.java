package serviceDeclarations;

public class NonService {
  public static NonService getInstance() {
    return new NonService();
  }
  public void foo() { }
}
