public class A {
public java.lang.Object getProp() {
 return prop;
}
public void setProp(java.lang.Object prop) {
this.prop = prop;
}
private java.lang.Object prop;
}
public class propAssignment extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new propAssignment(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {


A a = new A();

print(3 + (setProp(a, 2 + 1)));
return null;

}

public propAssignment(groovy.lang.Binding binding) {
super(binding);
}
public propAssignment() {
super();
}
private static <Value>Value setProp(A propOwner, Value prop) {
propOwner.setProp(prop);
return prop;
}
}
