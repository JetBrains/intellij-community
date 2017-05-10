import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class a2 implements EntityResolver {
   final bc a;
   private static final String b;

   a2(bc var1) {
      this.a = var1;
   }

   public InputSource resolveEntity(String var1, String var2) throws SAXException, IOException {
      URL var3 = new URL(var2);
      if (b.equals(var3.getProtocol())) {
         File var4 = new File(var3.getFile());

         try {
            if (var4.exists()) {
               return new InputSource(new FileInputStream(var4));
            }
         } catch (SAXException var5) {
            throw var5;
         }
      }

      return null;
   }

   static {
      char[] var10000 = "\u001d2\u00049".toCharArray();
      int var10002 = var10000.length;
      int var1 = 0;
      char[] var10001 = var10000;
      int var2 = var10002;
      int var10003;
      char[] var4;
      if (var10002 <= 1) {
         var4 = var10000;
         var10003 = var1;
      } else {
         var10001 = var10000;
         var2 = var10002;
         if (var10002 <= var1) {
            b = (new String(var10000)).intern();
            return;
         }

         var4 = var10000;
         var10003 = var1;
      }

      while(true) {
         char var10004 = var4[var10003];
         byte var10005;
         switch(var1 % 5) {
         case 0:
            var10005 = 123;
            break;
         case 1:
            var10005 = 91;
            break;
         case 2:
            var10005 = 104;
            break;
         case 3:
            var10005 = 92;
            break;
         default:
            var10005 = 15;
         }

         var4[var10003] = (char)(var10004 ^ var10005);
         ++var1;
         if (var2 == 0) {
            var10003 = var2;
            var4 = var10001;
         } else {
            if (var2 <= var1) {
               b = (new String(var10001)).intern();
               return;
            }

            var4 = var10001;
            var10003 = var1;
         }
      }
   }
}
