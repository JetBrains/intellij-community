java.lang.String[] commands = new java.lang.String[]{"abc"};
for(java.lang.String command : commands){
if (org.codehaus.groovy.runtime.StringGroovyMethods.isCase("abc", command)) {
print(1);
return 4;
}
else if (org.codehaus.groovy.runtime.StringGroovyMethods.isCase("start", command)) {
return 4;
}
else if (org.codehaus.groovy.runtime.StringGroovyMethods.isCase("next", command)) {
continue;
}
else {
return 0;
}
}

