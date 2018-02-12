import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Hashtable;
import java.util.Vector;

public class bd {
   private static String a;
   private static String j;
   private static MessageDigest b;
   private static Hashtable c;
   private static Hashtable d;
   private static final boolean e = false;
   private static String f;
   private static Hashtable g;
   private static Hashtable h;
   private static final String x = "";
   private static PrintWriter writer;
   private static final String[] i;

   private static void a(Hashtable var0, MessageDigest var1) {
      var0.put(new BigInteger(i[46], 36), "\uffc0");
      var0.put(new BigInteger(i[17], 36), "ﾏ");
      var0.put(new BigInteger(i[96], 36), "ﾣ");
      var0.put(new BigInteger(i[94], 36), "ￍ");
      var0.put(new BigInteger(i[24], 36), "\t");
      var0.put(new BigInteger(i[82], 36), i[60]);
      var0.put(new BigInteger(i[64], 36), "\uffbf");
      var0.put(new BigInteger(i[36], 36), "ￏ");
      var0.put(new BigInteger(i[56], 36), i[10]);
      var0.put(new BigInteger(i[30], 36), i[70]);
      var0.put(new BigInteger(i[75], 36), i[23]);
      var0.put(new BigInteger(i[47], 36), i[5]);
      var0.put(new BigInteger(i[19], 36), "ﾖ");
      var0.put(new BigInteger(i[2], 36), i[71]);
      var0.put(new BigInteger(i[50], 36), "ￊ");
      var0.put(new BigInteger(i[76], 36), "O");
      var0.put(new BigInteger(i[40], 36), i[15]);
      var0.put(new BigInteger(i[80], 36), i[33]);
      var0.put(new BigInteger(i[95], 36), i[38]);
      var0.put(new BigInteger(i[89], 36), i[69]);
      var0.put(new BigInteger(i[43], 36), ",");
      var0.put(new BigInteger(i[93], 36), i[77]);
      var0.put(new BigInteger(i[62], 36), "ﾊ");
      var0.put(new BigInteger(i[22], 36), i[66]);
      var0.put(new BigInteger(i[14], 36), i[34]);
      var0.put(new BigInteger(i[29], 36), i[49]);
      var0.put(new BigInteger(i[88], 36), i[25]);
      var0.put(new BigInteger(i[42], 36), i[39]);
      var0.put(new BigInteger(i[45], 36), i[11]);
      var0.put(new BigInteger(i[102], 36), i[73]);
      var0.put(new BigInteger(i[86], 36), i[0]);
      var0.put(new BigInteger(i[41], 36), i[100]);
      var0.put(new BigInteger(i[74], 36), i[51]);
      var0.put(new BigInteger(i[103], 36), i[65]);
      var0.put(new BigInteger(i[97], 36), i[85]);
      var0.put(new BigInteger(i[92], 36), i[104]);
      var0.put(new BigInteger(i[13], 36), i[20]);
      var0.put(new BigInteger(i[78], 36), i[90]);
      var0.put(new BigInteger(i[6], 36), ">");
      var0.put(new BigInteger(i[32], 36), "\uffdd");
      var0.put(new BigInteger(i[63], 36), "ￛ");
      var0.put(new BigInteger(i[53], 36), "\uffd0");
      var0.put(new BigInteger(i[7], 36), i[12]);
      var0.put(new BigInteger(i[54], 36), i[67]);
      var0.put(new BigInteger(i[4], 36), "ﾗ");
      var0.put(new BigInteger(i[3], 36), i[57]);
      var0.put(new BigInteger(i[79], 36), "\uffd0");
      var0.put(new BigInteger(i[16], 36), "A");
      var0.put(new BigInteger(i[44], 36), i[18]);
      var0.put(new BigInteger(i[31], 36), i[58]);
      var0.put(new BigInteger(i[21], 36), i[55]);
      var0.put(new BigInteger(i[35], 36), "-");
      var0.put(new BigInteger(i[9], 36), i[87]);
      var0.put(new BigInteger(i[61], 36), i[81]);
      var0.put(new BigInteger(i[72], 36), i[48]);
      var0.put(new BigInteger(i[68], 36), "\u0010");
      var0.put(new BigInteger(i[26], 36), "ﾜ");
      var0.put(new BigInteger(i[91], 36), i[1]);
      var0.put(new BigInteger(i[28], 36), i[101]);
      var0.put(new BigInteger(i[37], 36), "u");
      var0.put(new BigInteger(i[99], 36), i[52]);
      var0.put(new BigInteger(i[105], 36), i[59]);
      var0.put(new BigInteger(i[27], 36), "ﾰ");
      var0.put(new BigInteger(i[8], 36), i[98]);
      var0.put(new BigInteger(i[83], 36), "\uffd0");
      var0.put(new BigInteger(i[84], 36), "\uffde");
   }

   private static void b(Hashtable var0, MessageDigest var1) {
   }

   private static void c(Hashtable var0, MessageDigest var1) {
   }

   private static void d(Hashtable var0, MessageDigest var1) {
   }

   private static void e(Hashtable var0, MessageDigest var1) {
   }

   private static void f(Hashtable var0, MessageDigest var1) {
   }

   private static void g(Hashtable var0, MessageDigest var1) {
   }

   private static void h(Hashtable var0, MessageDigest var1) {
   }

   private static void i(Hashtable var0, MessageDigest var1) {
   }

   private static void j(Hashtable var0, MessageDigest var1) {
   }

   public static String a(String var0) {
      if (b == null) {
         return var0;
      } else {
         try {
            int var1 = var0.lastIndexOf("[") + 1;
            String var2 = var0.substring(var1);
            if (var1 > 0 && var2.length() == 1) {
               return var0;
            } else {
               boolean var3 = false;
               if (var2.charAt(0) == 'L' && var2.charAt(var2.length() - 1) == ';') {
                  var3 = true;
                  var2 = var2.substring(1, var2.length() - 1);
               }

               boolean var4 = var2.indexOf(46) > -1;
               if (var4) {
                  var2 = var2.replace('.', '/');
               }

               var2 = var2 + f;
               String var5 = b(var2);
               if (var5 == null) {
                  return var0;
               } else {
                  if (var4) {
                     var5 = var5.replace('/', '.');
                  }

                  StringBuffer var6 = new StringBuffer();

                  for(int var7 = 0; var7 < var1; ++var7) {
                     var6.append('[');
                  }

                  if (var3) {
                     var6.append('L');
                  }

                  var6.append(var5);
                  if (var3) {
                     var6.append(';');
                  }

                  return var6.toString();
               }
            }
         } catch (Throwable var8) {
            return var0;
         }
      }
   }

   public static String b(String var0, Class var1, Class[] var2) {
      if (b != null && var1 != null) {
         try {
            String var3 = var1.getName();
            String var4 = var3.replace('.', '/');
            StringBuffer var5 = new StringBuffer();
            var5.append(f);
            var5.append(var0);
            var5.append(f);
            if (var2 != null && var2.length > 0) {
               for(int var6 = 0; var6 < var2.length; ++var6) {
                  Class var7 = var2[var6];
                  var5.append(d.containsKey(var7) ? (String)d.get(var7) : var7.getName().replace('.', '/'));
                  var5.append(f);
               }
            }

            String var10 = var5.toString();
            String var11 = var4 + var10;
            String var8 = b(var11);
            if (var8 != null) {
               return var8;
            } else {
               var8 = a(var1, var10);
               return var8 != null ? var8 : var0;
            }
         } catch (Throwable var9) {
            return var0;
         }
      } else {
         return var0;
      }
   }

   public static String c(Class var0, String var1) {
      if (b != null && var0 != null) {
         try {
            String var2 = var0.getName();
            String var3 = var2.replace('.', '/');
            StringBuffer var4 = new StringBuffer();
            var4.append(f);
            var4.append(var1);
            String var5 = var4.toString();
            String var6 = var3 + var5;
            String var7 = b(var6);
            if (var7 != null) {
               return var7;
            } else {
               var7 = a(var0, var5);
               return var7 != null ? var7 : var1;
            }
         } catch (Throwable var8) {
            return var1;
         }
      } else {
         return var1;
      }
   }

   private static String b(String var0) {
      String var1 = (String)g.get(var0);
      if (var1 == null && var1 != "") {
         b.reset();

         try {
            b.update(var0.getBytes(j));
         } catch (UnsupportedEncodingException var4) {
            ;
         }

         byte[] var2 = b.digest();
         BigInteger var3 = new BigInteger(var2);
         var1 = (String)c.get(var3);
         if (var1 != null) {
            var1 = a(var0, var1);
            g.put(var0, var1);
         } else {
            g.put(var0, "");
         }
      }

      return var1 == "" ? null : var1;
   }

   private static String a(String var0, String var1) {
      b.reset();
      byte[] var2 = null;

      try {
         var2 = (var0 + a).getBytes(j);
      } catch (UnsupportedEncodingException var9) {
         ;
      }

      b.update(var2);
      byte[] var3 = b.digest();
      char[] var4 = var1.toCharArray();
      StringBuffer var5 = new StringBuffer(var4.length);

      for(int var6 = 0; var6 < var4.length; ++var6) {
         char var7 = var4[var6];
         byte var8;
         if (var6 < var3.length - 1) {
            var8 = var3[var6];
         } else {
            var8 = var3[var6 % var3.length];
         }

         var5.append((char)(var7 ^ (char)var8));
      }

      String var10 = var5.toString();
      return var10;
   }

   private static String a(Class var0, String var1) {
      Vector var2 = b(var0);
      int var3 = var2.size();

      for(int var4 = 0; var4 < var3; ++var4) {
         String var5 = (String)var2.elementAt(var4);
         String var6 = var5 + var1;
         String var7 = b(var6);
         if (var7 != null) {
            return var7;
         }
      }

      return null;
   }

   private static String a(Class var0) {
      return d.containsKey(var0) ? (String)d.get(var0) : var0.getName().replace('.', '/');
   }

   private static Vector b(Class var0) {
      String var1 = var0.getName();
      Vector var2 = (Vector)h.get(var1);
      if (var2 != null) {
         return var2;
      } else {
         Vector var3 = new Vector();
         Hashtable var4 = new Hashtable();
         b(var0, var3, var4);
         h.put(var1, var3);
         return var3;
      }
   }

   private static void b(Class var0, Vector var1, Hashtable var2) {
      Class var3 = var0.getSuperclass();
      if (var3 != null && !var2.containsKey(var3)) {
         var1.addElement(var3.getName().replace('.', '/'));
         var2.put(var3, var3);
         b(var3, var1, var2);
      }

      Class[] var4 = var0.getInterfaces();

      for(int var5 = 0; var5 < var4.length; ++var5) {
         Class var6 = var4[var5];
         if (!var2.containsKey(var6)) {
            var1.addElement(var6.getName().replace('.', '/'));
            var2.put(var6, var6);
            b(var6, var1, var2);
         }
      }

   }

   private static String c(Class var0) {
      return var0.getName().replace('.', '/');
   }

   static {
      // $FF: Couldn't be decompiled
   }
}
