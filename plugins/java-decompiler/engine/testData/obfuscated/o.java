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

      String[] var2 = var1;
      int var3 = var1.length;

      for(int var4 = 0; var4 < var3; ++var4) {
         String var5 = var2[var4];

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
            return null;
         }
      } catch (Exception var4) {
         throw var4;
      }

      try {
         if (var1.isAssignableFrom(this.a.getClass())) {
            return (Enum)this.a;
         }
      } catch (Exception var5) {
         throw var5;
      }

      try {
         return Enum.valueOf(var1, String.valueOf(this.a));
      } catch (Exception var3) {
         return null;
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
         var1 *= -1;

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
         var1 *= -1;

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
      char[] var10000;
      int var1;
      char[] var10001;
      int var10002;
      int var2;
      int var10003;
      char[] var4;
      char var10004;
      byte var10005;
      label51: {
         var10000 = "\t!\"\u0000r>`/\u0001s<%>\u001a=>/vN".toCharArray();
         var10002 = var10000.length;
         var1 = 0;
         var10001 = var10000;
         var2 = var10002;
         if (var10002 <= 1) {
            var4 = var10000;
            var10003 = var1;
         } else {
            var10001 = var10000;
            var2 = var10002;
            if (var10002 <= var1) {
               break label51;
            }

            var4 = var10000;
            var10003 = var1;
         }

         while(true) {
            var10004 = var4[var10003];
            switch(var1 % 5) {
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

            var4[var10003] = (char)(var10004 ^ var10005);
            ++var1;
            if (var2 == 0) {
               var10003 = var2;
               var4 = var10001;
            } else {
               if (var2 <= var1) {
                  break;
               }

               var4 = var10001;
               var10003 = var1;
            }
         }
      }

      c = (new String(var10001)).intern();
      var10000 = "\u0016$gFAd\u001c(E4u".toCharArray();
      var10002 = var10000.length;
      var1 = 0;
      var10001 = var10000;
      var2 = var10002;
      if (var10002 <= 1) {
         var4 = var10000;
         var10003 = var1;
      } else {
         var10001 = var10000;
         var2 = var10002;
         if (var10002 <= var1) {
            b = Pattern.compile((new String(var10000)).intern());
            return;
         }

         var4 = var10000;
         var10003 = var1;
      }

      while(true) {
         var10004 = var4[var10003];
         switch(var1 % 5) {
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

         var4[var10003] = (char)(var10004 ^ var10005);
         ++var1;
         if (var2 == 0) {
            var10003 = var2;
            var4 = var10001;
         } else {
            if (var2 <= var1) {
               b = Pattern.compile((new String(var10001)).intern());
               return;
            }

            var4 = var10001;
            var10003 = var1;
         }
      }
   }
}
