public class anonymous extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new anonymous(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
final java.lang.Integer foo = 2;
java.lang.Runnable an = new java.lang.Runnable(){
public void run() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.println(this, foo);
}

};

an.run();

println(foo);
return null;

}

public anonymous(groovy.lang.Binding binding) {
super(binding);
}
public anonymous() {
super();
}
}
