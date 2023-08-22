package one;


public class Hierarchy extends Proxy {
  void classMembers() {
    this.getProperty();

    int fieldProperty = this.fieldProperty;

    this.getVariable();
    this.setVariable(42);

    this.fieldVariable = 4;
    int fieldVariable = this.fieldVariable;

    Custom lateinitVariable = this.lateinitVariable;
    this.lateinitVariable = new Custom();
    this.setLateinitVariable(lateinitVariable);
    this.getLateinitVariable();
    
    this.simpleFunction(42);

    this.extension("awd");
  }
}
