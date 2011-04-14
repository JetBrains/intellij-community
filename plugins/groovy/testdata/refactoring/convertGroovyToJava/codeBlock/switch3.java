java.util.Date date = new Date(2011, 4, 9);
if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new Date(20, 11, 23), date)) {
print("aaa");
print("bbb");
}
else if (org.codehaus.groovy.runtime.DefaultGroovyMethods.isCase(new Date(45, 1, 2), date)) {
print("bbb");
}
else print("ccc");

