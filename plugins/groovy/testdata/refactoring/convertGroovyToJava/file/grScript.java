public class grScript extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new grScript(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
print("foo");
if (true){
print("false");
}
 else {
print("true");
java.lang.Integer a = 5;
}

return null;

}

public grScript(groovy.lang.Binding binding) {
super(binding);
}
public grScript() {
super();
}
}
