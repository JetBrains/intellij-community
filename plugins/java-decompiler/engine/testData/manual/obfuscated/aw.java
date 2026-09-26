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
         var41 = var10003;
         var8 = var10005;
         if (var10005 <= var1) {
            label127: {
               var10000[0] = (new String(var10003)).intern();
               char[] var26 = "1\u0013e".toCharArray();
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
            char[] var33 = "6\u0007".toCharArray();
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
                  f = var10000;
                  return;
               }

               var111 = var33;
               var10006 = var1;
            }

            while(true) {
               var10007 = var111[var10006];
               switch (var1 % 5) {
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

               var111[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var36 == 0) {
                  var10006 = var36;
                  var111 = var41;
               } else {
                  if (var36 <= var1) {
                     var10000[2] = (new String(var41)).intern();
                     f = var10000;
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
         var71[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var8 == 0) {
            var10006 = var8;
            var71 = var41;
            var10007 = var41[var8];
            switch (var1 % 5) {
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
            if (var8 <= var1) {
               label65: {
                  var10000[0] = (new String(var41)).intern();
                  char[] var12 = "1\u0013e".toCharArray();
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
               char[] var19 = "6\u0007".toCharArray();
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
                     f = var10000;
                     return;
                  }

                  var91 = var19;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var91[var10006];
                  switch (var1 % 5) {
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

                  var91[var10006] = (char)(var10007 ^ var10008);
                  ++var1;
                  if (var22 == 0) {
                     var10006 = var22;
                     var91 = var41;
                  } else {
                     if (var22 <= var1) {
                        var10000[2] = (new String(var41)).intern();
                        f = var10000;
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
