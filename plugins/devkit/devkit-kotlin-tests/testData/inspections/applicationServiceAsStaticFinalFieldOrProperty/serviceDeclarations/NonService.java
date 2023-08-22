package serviceDeclarations;

import com.intellij.openapi.application.ApplicationManager;

public class NonService {
  public static NonService getInstance() {
    return new NonService();
  }
  public void foo() { }
}
