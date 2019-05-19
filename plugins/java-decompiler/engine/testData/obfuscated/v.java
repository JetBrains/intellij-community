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
      boolean var10001;
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
            var10001 = false;
            throw var10000;
         }
      }

      try {
         t.a.warning(var1.getClass() + "." + var0.getName() + a[2]);
      } catch (Throwable var4) {
         var10000 = var4;
         var10001 = false;
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
      char[] var10004 = var10003;
      int var2 = var10005;
      char[] var4;
      int var10006;
      char var10007;
      byte var10008;
      if (var10005 <= 1) {
         var4 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch(var1 % 5) {
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
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label127: {
               var10000[0] = (new String(var10003)).intern();
               var10003 = "]\u0007".toCharArray();
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
                     break label127;
                  }

                  var4 = var10003;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var4[var10006];
                  switch(var1 % 5) {
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

            var10000[1] = (new String(var10004)).intern();
            var10003 = "]\u0007lg3\rBOZ\u0011\u000eTX\u000e/\u0002VYG/\u0002C\fO}\rFZOs\u0012SEBs+N_Za\"\u0019\fO.GAEK1\u0003\u0007XW-\u0002".toCharArray();
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
                  var10000[2] = (new String(var10003)).intern();
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

               var4[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var2 == 0) {
                  var10006 = var2;
                  var4 = var10004;
               } else {
                  if (var2 <= var1) {
                     var10000[2] = (new String(var10004)).intern();
                     a = var10000;
                     return;
                  }

                  var4 = var10004;
                  var10006 = var1;
               }
            }
         }

         var4 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch(var1 % 5) {
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
         while(true) {
            var4[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var2 == 0) {
               var10006 = var2;
               var4 = var10004;
               var10007 = var10004[var2];
               switch(var1 % 5) {
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
               if (var2 <= var1) {
                  label65: {
                     var10000[0] = (new String(var10004)).intern();
                     var10003 = "]\u0007".toCharArray();
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
                           break label65;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
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

                  var10000[1] = (new String(var10004)).intern();
                  var10003 = "]\u0007lg3\rBOZ\u0011\u000eTX\u000e/\u0002VYG/\u0002C\fO}\rFZOs\u0012SEBs+N_Za\"\u0019\fO.GAEK1\u0003\u0007XW-\u0002".toCharArray();
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
                        var10000[2] = (new String(var10003)).intern();
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

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[2] = (new String(var10004)).intern();
                           a = var10000;
                           return;
                        }

                        var4 = var10004;
                        var10006 = var1;
                     }
                  }
               }

               var4 = var10004;
               var10006 = var1;
               var10007 = var10004[var1];
               switch(var1 % 5) {
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
}
