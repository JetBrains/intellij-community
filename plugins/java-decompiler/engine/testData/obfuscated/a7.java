import java.io.FileInputStream;
import java.io.InputStream;

public class a7 {
   public static boolean b;
   private static final String[] a;

   public static void main(String[] var0) throws Exception {
      bc var1 = new bc();
      var1.a(a[0], new a8());
      var1.a((InputStream)(new FileInputStream(a[1])));
   }

   static {
      String[] var10000;
      int var1;
      int var2;
      char[] var10003;
      char[] var10004;
      char[] var4;
      int var10005;
      int var10006;
      char var10007;
      byte var10008;
      label51: {
         var10000 = new String[2];
         var10003 = "Z7wN".toCharArray();
         var10005 = var10003.length;
         var1 = 0;
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= 1) {
            var4 = var10003;
            var10006 = var1;
         } else {
            var10004 = var10003;
            var2 = var10005;
            if (var10005 <= var1) {
               break label51;
            }

            var4 = var10003;
            var10006 = var1;
         }

         while(true) {
            var10007 = var4[var10006];
            switch(var1 % 5) {
            case 0:
               var10008 = 52;
               break;
            case 1:
               var10008 = 88;
               break;
            case 2:
               var10008 = 19;
               break;
            case 3:
               var10008 = 43;
               break;
            default:
               var10008 = 10;
            }

            var4[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var2 == 0) {
               var10006 = var2;
               var4 = var10004;
            } else {
               if (var2 <= var1) {
                  break;
               }

               var4 = var10004;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var10004)).intern();
      var10003 = "G*p\u0004oL9~[fQ+<_oG,=SgX".toCharArray();
      var10005 = var10003.length;
      var1 = 0;
      var10004 = var10003;
      var2 = var10005;
      if (var10005 <= 1) {
         var4 = var10003;
         var10006 = var1;
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            var10000[1] = (new String(var10003)).intern();
            a = var10000;
            return;
         }

         var4 = var10003;
         var10006 = var1;
      }

      while(true) {
         var10007 = var4[var10006];
         switch(var1 % 5) {
         case 0:
            var10008 = 52;
            break;
         case 1:
            var10008 = 88;
            break;
         case 2:
            var10008 = 19;
            break;
         case 3:
            var10008 = 43;
            break;
         default:
            var10008 = 10;
         }

         var4[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var2 == 0) {
            var10006 = var2;
            var4 = var10004;
         } else {
            if (var2 <= var1) {
               var10000[1] = (new String(var10004)).intern();
               a = var10000;
               return;
            }

            var4 = var10004;
            var10006 = var1;
         }
      }
   }
}
