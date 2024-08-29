import java.math.BigDecimal;
import java.util.regex.Pattern;

public class o {
   private Object a;
   private static final Pattern b;
   private static final String c;

   public boolean a() {
      boolean var10000;
      try {
         if (this.a == null) {
            var10000 = true;
            return var10000;
         }
      } catch (IllegalArgumentException var1) {
         throw var1;
      }

      var10000 = false;
      return var10000;
   }

   public boolean b() {
      // $FF: Couldn't be decompiled
   }

   public boolean c() {
      boolean var10000;
      try {
         if (!this.b()) {
            var10000 = true;
            return var10000;
         }
      } catch (IllegalArgumentException var1) {
         throw var1;
      }

      var10000 = false;
      return var10000;
   }

   public o a(String... var1) {
      try {
         if (this.b()) {
            return this;
         }
      } catch (IllegalArgumentException var7) {
         throw var7;
      }

      for(String var5 : var1) {
         try {
            if (this.a.equals(var5)) {
               return b((Object)null);
            }
         } catch (IllegalArgumentException var6) {
            throw var6;
         }
      }

      return this;
   }

   public boolean d() {
      // $FF: Couldn't be decompiled
   }

   public Object e() {
      return this.a;
   }

   public Object a(Object var1) {
      Object var10000;
      try {
         if (this.a == null) {
            var10000 = var1;
            return var10000;
         }
      } catch (IllegalArgumentException var2) {
         throw var2;
      }

      var10000 = this.a;
      return var10000;
   }

   public <T> T a(Class<?> param1, T param2) {
      // $FF: Couldn't be decompiled
   }

   public <V> V b(Class<V> param1, V param2) {
      // $FF: Couldn't be decompiled
   }

   public String f() {
      String var10000;
      try {
         if (this.a()) {
            var10000 = null;
            return var10000;
         }
      } catch (IllegalArgumentException var1) {
         throw var1;
      }

      var10000 = this.g();
      return var10000;
   }

   public String g() {
      String var10000;
      try {
         if (this.a == null) {
            var10000 = "";
            return var10000;
         }
      } catch (IllegalArgumentException var1) {
         throw var1;
      }

      var10000 = this.a.toString();
      return var10000;
   }

   public String a(String var1) {
      String var10000;
      try {
         if (this.a()) {
            var10000 = var1;
            return var10000;
         }
      } catch (IllegalArgumentException var2) {
         throw var2;
      }

      var10000 = this.g();
      return var10000;
   }

   public boolean a(boolean var1) {
      try {
         if (this.a()) {
            return var1;
         }
      } catch (IllegalArgumentException var2) {
         throw var2;
      }

      try {
         if (this.a instanceof Boolean) {
            return (Boolean)this.a;
         }
      } catch (IllegalArgumentException var3) {
         throw var3;
      }

      return Boolean.parseBoolean(String.valueOf(this.a));
   }

   public boolean h() {
      return this.a(false);
   }

   public int a(int param1) {
      // $FF: Couldn't be decompiled
   }

   public Integer i() {
      // $FF: Couldn't be decompiled
   }

   public long a(long param1) {
      // $FF: Couldn't be decompiled
   }

   public Long j() {
      // $FF: Couldn't be decompiled
   }

   public double a(double param1) {
      // $FF: Couldn't be decompiled
   }

   public BigDecimal a(BigDecimal param1) {
      // $FF: Couldn't be decompiled
   }

   public static o b(Object var0) {
      o var1 = new o();
      var1.a = var0;
      return var1;
   }

   public String toString() {
      return this.g();
   }

   public <E extends Enum<E>> E a(Class<E> var1) {
      try {
         if (this.a == null) {
            return (E)null;
         }
      } catch (Exception var4) {
         throw var4;
      }

      try {
         if (var1.isAssignableFrom(this.a.getClass())) {
            return (E)(this.a);
         }
      } catch (Exception var5) {
         throw var5;
      }

      try {
         return (E)Enum.valueOf(var1, String.valueOf(this.a));
      } catch (Exception var3) {
         return (E)null;
      }
   }

   public String b(int var1) {
      String var2 = this.g();

      try {
         if (var2 == null) {
            return null;
         }
      } catch (IllegalArgumentException var3) {
         throw var3;
      }

      if (var1 < 0) {
         var1 = var1 * -1;

         try {
            if (var2.length() < var1) {
               return "";
            }
         } catch (IllegalArgumentException var4) {
            throw var4;
         }

         return var2.substring(var1);
      } else {
         try {
            if (var2.length() < var1) {
               return var2;
            }
         } catch (IllegalArgumentException var5) {
            throw var5;
         }

         return var2.substring(0, var1);
      }
   }

   public String c(int var1) {
      String var2 = this.g();

      try {
         if (var2 == null) {
            return null;
         }
      } catch (IllegalArgumentException var3) {
         throw var3;
      }

      if (var1 < 0) {
         var1 = var1 * -1;

         try {
            if (var2.length() < var1) {
               return var2;
            }
         } catch (IllegalArgumentException var4) {
            throw var4;
         }

         return var2.substring(0, var2.length() - var1);
      } else {
         try {
            if (var2.length() < var1) {
               return var2;
            }
         } catch (IllegalArgumentException var5) {
            throw var5;
         }

         return var2.substring(var2.length() - var1);
      }
   }

   public String a(int var1, int var2) {
      String var3 = this.g();

      try {
         if (var3 == null) {
            return null;
         }
      } catch (IllegalArgumentException var4) {
         throw var4;
      }

      try {
         if (var1 > var3.length()) {
            return "";
         }
      } catch (IllegalArgumentException var5) {
         throw var5;
      }

      return var3.substring(var1, Math.min(var3.length(), var2));
   }

   public int k() {
      String var1 = this.g();

      try {
         if (var1 == null) {
            return 0;
         }
      } catch (IllegalArgumentException var2) {
         throw var2;
      }

      return var1.length();
   }

   public boolean b(Class<?> param1) {
      // $FF: Couldn't be decompiled
   }

   static {
      char[] var17;
      label51: {
         char[] var10000 = "\t!\"\u0000r>`/\u0001s<%>\u001a=>/vN".toCharArray();
         int var10002 = var10000.length;
         int var1 = 0;
         var17 = var10000;
         int var5 = var10002;
         char[] var29;
         int var10003;
         if (var10002 <= 1) {
            var29 = var10000;
            var10003 = var1;
         } else {
            var17 = var10000;
            var5 = var10002;
            if (var10002 <= var1) {
               break label51;
            }

            var29 = var10000;
            var10003 = var1;
         }

         while(true) {
            char var10004 = var29[var10003];
            byte var10005;
            switch (var1 % 5) {
               case 0:
                  var10005 = 74;
                  break;
               case 1:
                  var10005 = 64;
                  break;
               case 2:
                  var10005 = 76;
                  break;
               case 3:
                  var10005 = 110;
                  break;
               default:
                  var10005 = 29;
            }

            var29[var10003] = (char)(var10004 ^ var10005);
            ++var1;
            if (var5 == 0) {
               var10003 = var5;
               var29 = var17;
            } else {
               if (var5 <= var1) {
                  break;
               }

               var29 = var17;
               var10003 = var1;
            }
         }
      }

      c = (new String(var17)).intern();
      char[] var9 = "\u0016$gFAd\u001c(E4u".toCharArray();
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
            b = Pattern.compile((new String(var9)).intern());
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
               var48 = 74;
               break;
            case 1:
               var48 = 64;
               break;
            case 2:
               var48 = 76;
               break;
            case 3:
               var48 = 110;
               break;
            default:
               var48 = 29;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               b = Pattern.compile((new String(var17)).intern());
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
