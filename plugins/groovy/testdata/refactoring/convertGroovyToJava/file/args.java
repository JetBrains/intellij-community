public class args extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new args(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
foo(new java.lang.String[]{"a", "b", "c"});
foo(new java.lang.String[]{"a"});
foo("a", 2);
foo(4);
return null;

}

public void foo(java.lang.String... args) {
}

public void foo(java.lang.String s, int x) {
}

public void foo(int x) {
foo("a", x);
}

public args(groovy.lang.Binding binding) {
super(binding);
}
public args() {
super();
}
}
