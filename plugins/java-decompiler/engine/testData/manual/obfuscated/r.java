import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

public class r {
   public static final Logger a;
   @x(
      a = q.class
   )
   private static List<q> b;
   private static final String[] c;

   public static s a(String var0) {
      return new s(var0);
   }

   private static a9 a(p param0) {
      // $FF: Couldn't be decompiled
   }

   public static a9 a(Throwable var0) {
      try {
         if (var0 instanceof a9) {
            return (a9)var0;
         }
      } catch (a9 var1) {
         throw var1;
      }

      try {
         if (var0 instanceof SQLException) {
            return a(c[0]).a(var0).a();
         }
      } catch (a9 var2) {
         throw var2;
      }

      return a(c[1]).a(var0).a();
   }

   static a9 b(p var0) {
      return a(var0);
   }

   static {
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "hzE\u0016\u0001nt\u0002\u0007\u000bi|\u000275W\\T\u0007\u0001kmE\u000b\n".toCharArray();
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
                  var10008 = 27;
                  break;
               case 1:
                  var10008 = 25;
                  break;
               case 2:
                  var10008 = 44;
                  break;
               case 3:
                  var10008 = 100;
                  break;
               default:
                  var10008 = 100;
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
      char[] var9 = "hzE\u0016\u0001nt\u0002\u0007\u000bi|\u00021\n~a\\\u0001\u0007o|H!\u001cx|\\\u0010\rtw".toCharArray();
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
            c = var10000;
            a = Logger.getLogger(r.class.getName());
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
               var48 = 27;
               break;
            case 1:
               var48 = 25;
               break;
            case 2:
               var48 = 44;
               break;
            case 3:
               var48 = 100;
               break;
            default:
               var48 = 100;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               var10000[1] = (new String(var17)).intern();
               c = var10000;
               a = Logger.getLogger(r.class.getName());
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
