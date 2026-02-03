Bar bar = new Bar();
if (!org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(bar))print("fail");
print(bar.bitwiseNegate());
print(bar.positive());
print(bar.negative());
