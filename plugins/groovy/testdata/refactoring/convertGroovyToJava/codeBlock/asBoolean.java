java.lang.String a = "foo";
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(a)){
print(a);
}
 else {
print("foo foo");
java.util.ArrayList<java.lang.Integer> integers = new java.util.ArrayList<java.lang.Integer>(3);
integers.add(1);
integers.add(2);
integers.add(3);
java.util.ArrayList<java.lang.Integer> list = integers;
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(list)?"full: " + java.lang.String.valueOf(list):"empty");
}

