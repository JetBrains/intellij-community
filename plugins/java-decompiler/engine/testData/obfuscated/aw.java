import java.lang.management.MemoryPoolMXBean;

class aw extends ap {
   private MemoryPoolMXBean d;
   final an e;
   private static final String[] f;

   public aw(an var1, MemoryPoolMXBean var2) {
      super(f[1], f[0] + var2.getName());
      this.e = var1;
      this.d = var2;
   }

   public double d() {
      // $FF: Couldn't be decompiled
   }

   public String c() {
      return f[2];
   }

   public Double e() {
      // $FF: Couldn't be decompiled
   }

   static {
      String[] var10000 = new String[3];
      char[] var10003 = "6\u0000ec".toCharArray();
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
            var10008 = 123;
            break;
         case 1:
            var10008 = 69;
            break;
         case 2:
            var10008 = 40;
            break;
         case 3:
            var10008 = 78;
            break;
         default:
            var10008 = 31;
         }
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label127: {
               var10000[0] = (new String(var10003)).intern();
               var10003 = "1\u0013e".toCharArray();
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
                     var10008 = 123;
                     break;
                  case 1:
                     var10008 = 69;
                     break;
                  case 2:
                     var10008 = 40;
                     break;
                  case 3:
                     var10008 = 78;
                     break;
                  default:
                     var10008 = 31;
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
            var10003 = "6\u0007".toCharArray();
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
                  f = var10000;
                  return;
               }

               var4 = var10003;
               var10006 = var1;
            }

            while(true) {
               var10007 = var4[var10006];
               switch(var1 % 5) {
               case 0:
                  var10008 = 123;
                  break;
               case 1:
                  var10008 = 69;
                  break;
               case 2:
                  var10008 = 40;
                  break;
               case 3:
                  var10008 = 78;
                  break;
               default:
                  var10008 = 31;
               }

               var4[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var2 == 0) {
                  var10006 = var2;
                  var4 = var10004;
               } else {
                  if (var2 <= var1) {
                     var10000[2] = (new String(var10004)).intern();
                     f = var10000;
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
            var10008 = 123;
            break;
         case 1:
            var10008 = 69;
            break;
         case 2:
            var10008 = 40;
            break;
         case 3:
            var10008 = 78;
            break;
         default:
            var10008 = 31;
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
                  var10008 = 123;
                  break;
               case 1:
                  var10008 = 69;
                  break;
               case 2:
                  var10008 = 40;
                  break;
               case 3:
                  var10008 = 78;
                  break;
               default:
                  var10008 = 31;
               }
            } else {
               if (var2 <= var1) {
                  label65: {
                     var10000[0] = (new String(var10004)).intern();
                     var10003 = "1\u0013e".toCharArray();
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
                           var10008 = 123;
                           break;
                        case 1:
                           var10008 = 69;
                           break;
                        case 2:
                           var10008 = 40;
                           break;
                        case 3:
                           var10008 = 78;
                           break;
                        default:
                           var10008 = 31;
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
                  var10003 = "6\u0007".toCharArray();
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
                        f = var10000;
                        return;
                     }

                     var4 = var10003;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var4[var10006];
                     switch(var1 % 5) {
                     case 0:
                        var10008 = 123;
                        break;
                     case 1:
                        var10008 = 69;
                        break;
                     case 2:
                        var10008 = 40;
                        break;
                     case 3:
                        var10008 = 78;
                        break;
                     default:
                        var10008 = 31;
                     }

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[2] = (new String(var10004)).intern();
                           f = var10000;
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
                  var10008 = 123;
                  break;
               case 1:
                  var10008 = 69;
                  break;
               case 2:
                  var10008 = 40;
                  break;
               case 3:
                  var10008 = 78;
                  break;
               default:
                  var10008 = 31;
               }
            }
         }
      }
   }
}
