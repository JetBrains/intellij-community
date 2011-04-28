print(new java.lang.Runnable() {
public void run(java.lang.Object it) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, "foo}");
}
public void run() {
this.run(null);
}
});
