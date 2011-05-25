java.lang.Integer a = 5;
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(a)?1:2);
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(a)?a:4);
java.lang.Boolean b = true;
print(b?3:4);
print(b?b:5);
print(b?:4);
final java.lang.Integer i = 3 - 4;
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(i)?i:4);
