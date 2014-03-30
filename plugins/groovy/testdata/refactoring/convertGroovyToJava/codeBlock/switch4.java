java.util.Date date = new java.util.Date(2011, 04, 09);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new java.util.Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1)), date)) {
print("bbb");
}
else {
print("ccc");
}
