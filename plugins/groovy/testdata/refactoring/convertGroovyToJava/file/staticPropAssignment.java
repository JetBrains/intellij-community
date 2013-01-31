public class A {
public static java.lang.Object getProp() {
 return prop;
}
public static void setProp(java.lang.Object prop) {
A.prop = prop;
}
private static java.lang.Object prop;
}
public class staticPropAssignment extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new staticPropAssignment(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {


print(3 + (setProp(2 + 1)));
return null;

}

public staticPropAssignment(groovy.lang.Binding binding) {
super(binding);
}
public staticPropAssignment() {
super();
}
private static <Value>Value setProp(Value prop) {
A.setProp(prop);
return prop;
}
}
