java.util.Date date = new java.util.Date(2011, 4, 9);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new java.util.Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else {
java.util.ArrayList<java.lang.Integer> integers = new java.util.ArrayList<java.lang.Integer>(1);
integers.add(1);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(integers, date)) {
print("bbb");
}
else print("ccc");
}

