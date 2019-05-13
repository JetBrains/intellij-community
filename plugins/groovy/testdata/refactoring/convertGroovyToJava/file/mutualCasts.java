public class X {
public void foo(java.util.List l) {
if (l instanceof java.lang.String){
if (l instanceof MyList){
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, l);
java.lang.String v = ((MyList)l).getValue();
}

}

}

}
public class MyList {
public java.lang.String getValue() {return "";}

}
