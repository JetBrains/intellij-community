String[] commands = new String[]{"abc"};
for (String command : commands) {
if (DefaultGroovyMethods.isCase("abc", command)) {
DefaultGroovyMethods.print(this, 1);
return 4;
} else if (DefaultGroovyMethods.isCase("start", command)) {
return 4;
} else if (DefaultGroovyMethods.isCase("next", command)) {
continue;
} else {
return 0;
}
}