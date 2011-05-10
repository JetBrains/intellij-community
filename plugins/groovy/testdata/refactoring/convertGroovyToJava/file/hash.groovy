import java.security.MessageDigest

int KB = 1024
int MB = 1024*KB

File f = new File(args[0])
if (!f.exists() || !f.isFile()) {
  println "Invalid file $f provided"
  println "Usage: groovy sha1.groovy <file_to_hash>"
}

def messageDigest = MessageDigest.getInstance("SHA1")

long start = System.currentTimeMillis()

f.eachByte(MB) { byte[] buf, int bytesRead ->
  messageDigest.update(buf, 0, bytesRead);
}

def sha1Hex = new BigInteger(1, messageDigest.digest()).toString(16).padLeft( 40, '0' )
long delta = System.currentTimeMillis()-start

println "$sha1Hex took $delta ms to calculate"