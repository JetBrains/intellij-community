public class BigInteger {
public BigInteger(int i, java.lang.Byte[] arr) {}
public java.lang.String toString(int radix) {return "";}

}
public class NoSuchAlgorithmException extends java.lang.Exception {
}
public class MessageDigest {
public static MessageDigest getInstance(java.lang.String algorithm) throws NoSuchAlgorithmException {
return new MessageDigest();
}

public java.lang.Byte[] digest() {return new java.lang.Byte[0];}

public void update(java.lang.Byte[] input, int offset, int len) {}

}
public class hash extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new hash(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {







int KB = 1024;
int MB = 1024 * KB;

java.io.File f = new java.io.File(this.getBinding().getProperty("args")[0]);
if (!f.exists() || !f.isFile()){
println("Invalid file " + java.lang.String.valueOf(f) + " provided");
println("Usage: groovy sha1.groovy <file_to_hash>");
}


final MessageDigest messageDigest = MessageDigest.getInstance("SHA1");

long start = java.lang.System.currentTimeMillis();

org.codehaus.groovy.runtime.ResourceGroovyMethods.eachByte(f, MB, new groovy.lang.Closure<java.lang.Object>(this, this) {
public void doCall(java.lang.Byte[] buf, int bytesRead) {
messageDigest.update(buf, 0, bytesRead);
}

});

java.lang.String sha1Hex = org.codehaus.groovy.runtime.StringGroovyMethods.padLeft(new BigInteger(1, messageDigest.digest()).toString(16), 40, "0");
long delta = java.lang.System.currentTimeMillis() - start;

println(sha1Hex + " took " + java.lang.String.valueOf(delta) + " ms to calculate");
return null;

}

public hash(groovy.lang.Binding binding) {
super(binding);
}
public hash() {
super();
}
}
