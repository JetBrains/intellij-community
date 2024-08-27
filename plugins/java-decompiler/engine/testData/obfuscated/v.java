import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Level;

public class v {
   private static final String[] a;

   public static Object a(Object var0) {
      try {
         if (var0 != null) {
            a(var0, var0.getClass());
         }

         return var0;
      } catch (IllegalArgumentException var1) {
         throw var1;
      }
   }

   private static void a(Object param0, Class<?> param1) {
      // $FF: Couldn't be decompiled
   }

   private static void a(Field var0, Object var1) {
      int var3 = y.d;

      Throwable var10000;
      label43: {
         try {
            if (var3 != 0) {
               return;
            }

            if (!List.class.isAssignableFrom(var0.getType())) {
               break label43;
            }
         } catch (Throwable var7) {
            throw var7;
         }

         Throwable var2;
         try {
            var0.set(var1, t.b(((x)var0.getAnnotation(x.class)).a()));
            return;
         } catch (Throwable var5) {
            var2 = var5;
         }

         try {
            t.a.log(Level.WARNING, var1.getClass() + "." + var0.getName() + a[1] + var2.getMessage(), var2);
            if (var3 == 0) {
               return;
            }
         } catch (Throwable var6) {
            var10000 = var6;
            boolean var10001 = false;
            throw var10000;
         }
      }

      try {
         t.a.warning(var1.getClass() + "." + var0.getName() + a[2]);
      } catch (Throwable var4) {
         var10000 = var4;
         boolean var8 = false;
         throw var10000;
      }
   }

   private static void b(Field var0, Object var1) {
      try {
         var0.set(var1, t.a(var0.getType()));
      } catch (Throwable var3) {
         t.a.log(Level.WARNING, var1.getClass() + "." + var0.getName() + a[0] + var3.getMessage(), var3);
      }

   }

   public <I> I a(Class<I> var1) {
      try {
         return a(var1.newInstance());
      } catch (Throwable var3) {
         throw new IllegalArgumentException(var3);
      }
   }

   static {
      String[] var10000 = new String[3];
      char[] var10003 = "]\u0007".toCharArray();
      int var10005 = var10003.length;
      int var1 = 0;
      char[] var41 = var10003;
      int var8 = var10005;
      char[] var71;
      int var10006;
      char var10007;
      byte var10008;
      if (var10005 <= 1) {
         var71 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch (var1 % 5) {
            case 0:
               var10008 = 103;
               break;
            case 1:
               var10008 = 39;
               break;
            case 2:
               var10008 = 44;
               break;
            case 3:
               var10008 = 46;
               break;
            default:
               var10008 = 93;
         }
      } else {
         var41 = var10003;
         var8 = var10005;
         if (var10005 <= var1) {
            label127: {
               var10000[0] = (new String(var10003)).intern();
               char[] var26 = "]\u0007".toCharArray();
               int var98 = var26.length;
               var1 = 0;
               var41 = var26;
               int var29 = var98;
               char[] var101;
               if (var98 <= 1) {
                  var101 = var26;
                  var10006 = var1;
               } else {
                  var41 = var26;
                  var29 = var98;
                  if (var98 <= var1) {
                     break label127;
                  }

                  var101 = var26;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var101[var10006];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 103;
                        break;
                     case 1:
                        var10008 = 39;
                        break;
                     case 2:
                        var10008 = 44;
                        break;
                     case 3:
                        var10008 = 46;
                        break;
                     default:
                        var10008 = 93;
                  }

                  var101[var10006] = (char)(var10007 ^ var10008);
                  ++var1;
                  if (var29 == 0) {
                     var10006 = var29;
                     var101 = var41;
                  } else {
                     if (var29 <= var1) {
                        break;
                     }

                     var101 = var41;
                     var10006 = var1;
                  }
               }
            }

            var10000[1] = (new String(var41)).intern();
            char[] var33 = "]\u0007lg3\rBOZ\u0011\u000eTX\u000e/\u0002VYG/\u0002C\fO}\rFZOs\u0012SEBs+N_Za\"\u0019\fO.GAEK1\u0003\u0007XW-\u0002".toCharArray();
            int var108 = var33.length;
            var1 = 0;
            var41 = var33;
            int var36 = var108;
            char[] var111;
            if (var108 <= 1) {
               var111 = var33;
               var10006 = var1;
            } else {
               var41 = var33;
               var36 = var108;
               if (var108 <= var1) {
                  var10000[2] = (new String(var33)).intern();
                  a = var10000;
                  return;
               }

               var111 = var33;
               var10006 = var1;
            }

            while(true) {
               var10007 = var111[var10006];
               switch (var1 % 5) {
                  case 0:
                     var10008 = 103;
                     break;
                  case 1:
                     var10008 = 39;
                     break;
                  case 2:
                     var10008 = 44;
                     break;
                  case 3:
                     var10008 = 46;
                     break;
                  default:
                     var10008 = 93;
               }

               var111[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var36 == 0) {
                  var10006 = var36;
                  var111 = var41;
               } else {
                  if (var36 <= var1) {
                     var10000[2] = (new String(var41)).intern();
                     a = var10000;
                     return;
                  }

                  var111 = var41;
                  var10006 = var1;
               }
            }
         }

         var71 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch (var1 % 5) {
            case 0:
               var10008 = 103;
               break;
            case 1:
               var10008 = 39;
               break;
            case 2:
               var10008 = 44;
               break;
            case 3:
               var10008 = 46;
               break;
            default:
               var10008 = 93;
         }
      }

      while(true) {
         var71[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var8 == 0) {
            var10006 = var8;
            var71 = var41;
            var10007 = var41[var8];
            switch (var1 % 5) {
               case 0:
                  var10008 = 103;
                  break;
               case 1:
                  var10008 = 39;
                  break;
               case 2:
                  var10008 = 44;
                  break;
               case 3:
                  var10008 = 46;
                  break;
               default:
                  var10008 = 93;
            }
         } else {
            if (var8 <= var1) {
               label65: {
                  var10000[0] = (new String(var41)).intern();
                  char[] var12 = "]\u0007".toCharArray();
                  int var78 = var12.length;
                  var1 = 0;
                  var41 = var12;
                  int var15 = var78;
                  char[] var81;
                  if (var78 <= 1) {
                     var81 = var12;
                     var10006 = var1;
                  } else {
                     var41 = var12;
                     var15 = var78;
                     if (var78 <= var1) {
                        break label65;
                     }

                     var81 = var12;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var81[var10006];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 103;
                           break;
                        case 1:
                           var10008 = 39;
                           break;
                        case 2:
                           var10008 = 44;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 93;
                     }

                     var81[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var15 == 0) {
                        var10006 = var15;
                        var81 = var41;
                     } else {
                        if (var15 <= var1) {
                           break;
                        }

                        var81 = var41;
                        var10006 = var1;
                     }
                  }
               }

               var10000[1] = (new String(var41)).intern();
               char[] var19 = "]\u0007lg3\rBOZ\u0011\u000eTX\u000e/\u0002VYG/\u0002C\fO}\rFZOs\u0012SEBs+N_Za\"\u0019\fO.GAEK1\u0003\u0007XW-\u0002".toCharArray();
               int var88 = var19.length;
               var1 = 0;
               var41 = var19;
               int var22 = var88;
               char[] var91;
               if (var88 <= 1) {
                  var91 = var19;
                  var10006 = var1;
               } else {
                  var41 = var19;
                  var22 = var88;
                  if (var88 <= var1) {
                     var10000[2] = (new String(var19)).intern();
                     a = var10000;
                     return;
                  }

                  var91 = var19;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var91[var10006];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 103;
                        break;
                     case 1:
                        var10008 = 39;
                        break;
                     case 2:
                        var10008 = 44;
                        break;
                     case 3:
                        var10008 = 46;
                        break;
                     default:
                        var10008 = 93;
                  }

                  var91[var10006] = (char)(var10007 ^ var10008);
                  ++var1;
                  if (var22 == 0) {
                     var10006 = var22;
                     var91 = var41;
                  } else {
                     if (var22 <= var1) {
                        var10000[2] = (new String(var41)).intern();
                        a = var10000;
                        return;
                     }

                     var91 = var41;
                     var10006 = var1;
                  }
               }
            }

            var71 = var41;
            var10006 = var1;
            var10007 = var41[var1];
            switch (var1 % 5) {
               case 0:
                  var10008 = 103;
                  break;
               case 1:
                  var10008 = 39;
                  break;
               case 2:
                  var10008 = 44;
                  break;
               case 3:
                  var10008 = 46;
                  break;
               default:
                  var10008 = 93;
            }
         }
      }
   }
}
