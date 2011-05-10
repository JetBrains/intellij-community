public class returns extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public void foo1() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(returns.this, "foo");
}

public void foo2() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(returns.this, "foo");
}

public int foo3() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(returns.this, "foo");
}

public java.lang.Integer foo4() {
return 1;
}

public java.lang.Integer foo5() {
return 2;
}

public int foo6() {
return 2;
}

public java.lang.Integer foo7() {
if (true)return 3; else return 4;
}

public int foo8() {
if (true)return 3; else return 4;
}

public void foo9() {
if (true)3; else 4;
}

public void foo10() {
if (true)return 3; else 4;
}

public java.lang.Integer foo11() {
if (true)if (false)return 4; else return 5; else return 4;
}

}
