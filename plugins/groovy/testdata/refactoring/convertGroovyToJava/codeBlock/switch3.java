java.util.Date date = new java.util.Date(2011, 4, 9);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new java.util.Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new java.util.Date(45, 1, 2), date)) {
print("bbb");
}
else {
print("ccc");
}
