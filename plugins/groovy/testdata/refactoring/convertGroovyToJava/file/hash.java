public class hash extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new hash(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
int KB = 1024;
int MB = 1024 * KB;
java.io.File f = new java.io.File(hash.this.getBinding().getProperty("args")[0]);
if (!org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(f.exists()) || !org.codehaus.groovy.runtime.DefaultGroovyMethods.asBoolean(f.isFile())){
println("Invalid file " + java.lang.String.valueOf(f) + " provided");
println("Usage: groovy sha1.groovy <file_to_hash>");
}

final java.lang.Object messageDigest = hash.this.getBinding().getProperty("MessageDigest").getInstance.call("SHA1");
long start = java.lang.System.currentTimeMillis();
org.codehaus.groovy.runtime.DefaultGroovyMethods.eachByte(f, MB, new groovy.lang.Closure<java.lang.Object>(this, this) {
public java.lang.Object doCall(java.lang.Byte[] buf, int bytesRead) {
return messageDigest.update.call(buf, 0, bytesRead);
}

});
java.lang.Object sha1Hex = new BigInteger(1, messageDigest.digest.call()).toString.call(16).padLeft.call(40, "0");
long delta = java.lang.System.currentTimeMillis() - start;
println(java.lang.String.valueOf(sha1Hex) + " took " + java.lang.String.valueOf(delta) + " ms to calculate");
return null;

}

public hash(groovy.lang.Binding binding) {
super(binding);
}
public hash() {
super();
}
}
