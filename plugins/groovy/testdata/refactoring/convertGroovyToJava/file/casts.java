public class X {
public X plus(X x) {return new X();}

}
public class casts extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new casts(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
java.lang.Object a = new java.util.Date();
java.util.Date d = (java.util.Date)a;



foo((java.util.Date)a);

d = ((java.util.Date)(a));

java.util.Date b = (java.util.Date)a;

a = org.codehaus.groovy.runtime.DateGroovyMethods.plus(a, 2);



X x = new X();

x = x.plus(new X());

x = x.plus(x);

print(x);

X y = true?x:new X();
return null;

}

public void foo(java.util.Date d) {}

public casts(groovy.lang.Binding binding) {
super(binding);
}
public casts() {
super();
}
}
