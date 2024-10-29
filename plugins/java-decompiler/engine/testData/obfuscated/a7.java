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
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "Z7wN".toCharArray();
         int var10005 = var10003.length;
         int var1 = 0;
         var17 = var10003;
         int var5 = var10005;
         char[] var29;
         int var10006;
         if (var10005 <= 1) {
            var29 = var10003;
            var10006 = var1;
         } else {
            var17 = var10003;
            var5 = var10005;
            if (var10005 <= var1) {
               break label51;
            }

            var29 = var10003;
            var10006 = var1;
         }

         while(true) {
            char var10007 = var29[var10006];
            byte var10008;
            switch (var1 % 5) {
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

            var29[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var5 == 0) {
               var10006 = var5;
               var29 = var17;
            } else {
               if (var5 <= var1) {
                  break;
               }

               var29 = var17;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var17)).intern();
      char[] var9 = "G*p\u0004oL9~[fQ+<_oG,=SgX".toCharArray();
      int var36 = var9.length;
      int var2 = 0;
      var17 = var9;
      int var12 = var36;
      char[] var39;
      int var46;
      if (var36 <= 1) {
         var39 = var9;
         var46 = var2;
      } else {
         var17 = var9;
         var12 = var36;
         if (var36 <= var2) {
            var10000[1] = (new String(var9)).intern();
            a = var10000;
            return;
         }

         var39 = var9;
         var46 = var2;
      }

      while(true) {
         char var47 = var39[var46];
         byte var48;
         switch (var2 % 5) {
            case 0:
               var48 = 52;
               break;
            case 1:
               var48 = 88;
               break;
            case 2:
               var48 = 19;
               break;
            case 3:
               var48 = 43;
               break;
            default:
               var48 = 10;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               var10000[1] = (new String(var17)).intern();
               a = var10000;
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
