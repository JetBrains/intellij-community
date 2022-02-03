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
         var10003 = "hzE\u0016\u0001nt\u0002\u0007\u000bi|\u000275W\\T\u0007\u0001kmE\u000b\n".toCharArray();
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
      var10003 = "hzE\u0016\u0001nt\u0002\u0007\u000bi|\u00021\n~a\\\u0001\u0007o|H!\u001cx|\\\u0010\rtw".toCharArray();
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
            c = var10000;
            a = Logger.getLogger(r.class.getName());
            return;
         }

         var4 = var10003;
         var10006 = var1;
      }

      while(true) {
         var10007 = var4[var10006];
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

         var4[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var2 == 0) {
            var10006 = var2;
            var4 = var10004;
         } else {
            if (var2 <= var1) {
               var10000[1] = (new String(var10004)).intern();
               c = var10000;
               a = Logger.getLogger(r.class.getName());
               return;
            }

            var4 = var10004;
            var10006 = var1;
         }
      }
   }
}
