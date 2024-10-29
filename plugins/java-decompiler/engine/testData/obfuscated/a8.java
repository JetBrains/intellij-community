final class a8 implements ay {
   private static final String[] a;

   public void a(a0 param1) {
      // $FF: Couldn't be decompiled
   }

   static {
      char[] var49;
      String[] var10000;
      label115: {
         var10000 = new String[4];
         char[] var10003 = "(\u000f7O".toCharArray();
         int var10005 = var10003.length;
         int var1 = 0;
         var49 = var10003;
         int var9 = var10005;
         char[] var85;
         int var10006;
         if (var10005 <= 1) {
            var85 = var10003;
            var10006 = var1;
         } else {
            var49 = var10003;
            var9 = var10005;
            if (var10005 <= var1) {
               break label115;
            }

            var85 = var10003;
            var10006 = var1;
         }

         while(true) {
            char var10007 = var85[var10006];
            byte var10008;
            switch (var1 % 5) {
               case 0:
                  var10008 = 70;
                  break;
               case 1:
                  var10008 = 110;
                  break;
               case 2:
                  var10008 = 90;
                  break;
               case 3:
                  var10008 = 42;
                  break;
               default:
                  var10008 = 64;
            }

            var85[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var9 == 0) {
               var10006 = var9;
               var85 = var49;
            } else {
               if (var9 <= var1) {
                  break;
               }

               var85 = var49;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var49)).intern();
      char[] var13 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
      int var92 = var13.length;
      int var2 = 0;
      var49 = var13;
      int var16 = var92;
      char[] var95;
      int var142;
      char var147;
      byte var152;
      if (var92 <= 1) {
         var95 = var13;
         var142 = var2;
         var147 = var13[var2];
         switch (var2 % 5) {
            case 0:
               var152 = 70;
               break;
            case 1:
               var152 = 110;
               break;
            case 2:
               var152 = 90;
               break;
            case 3:
               var152 = 42;
               break;
            default:
               var152 = 64;
         }
      } else {
         var49 = var13;
         var16 = var92;
         if (var92 <= var2) {
            label158: {
               var10000[1] = (new String(var13)).intern();
               char[] var34 = "6\u001c3I%".toCharArray();
               int var122 = var34.length;
               var2 = 0;
               var49 = var34;
               int var37 = var122;
               char[] var125;
               if (var122 <= 1) {
                  var125 = var34;
                  var142 = var2;
               } else {
                  var49 = var34;
                  var37 = var122;
                  if (var122 <= var2) {
                     break label158;
                  }

                  var125 = var34;
                  var142 = var2;
               }

               while(true) {
                  var147 = var125[var142];
                  switch (var2 % 5) {
                     case 0:
                        var152 = 70;
                        break;
                     case 1:
                        var152 = 110;
                        break;
                     case 2:
                        var152 = 90;
                        break;
                     case 3:
                        var152 = 42;
                        break;
                     default:
                        var152 = 64;
                  }

                  var125[var142] = (char)(var147 ^ var152);
                  ++var2;
                  if (var37 == 0) {
                     var142 = var37;
                     var125 = var49;
                  } else {
                     if (var37 <= var2) {
                        break;
                     }

                     var125 = var49;
                     var142 = var2;
                  }
               }
            }

            var10000[2] = (new String(var49)).intern();
            char[] var41 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
            int var132 = var41.length;
            var2 = 0;
            var49 = var41;
            int var44 = var132;
            char[] var135;
            if (var132 <= 1) {
               var135 = var41;
               var142 = var2;
            } else {
               var49 = var41;
               var44 = var132;
               if (var132 <= var2) {
                  var10000[3] = (new String(var41)).intern();
                  a = var10000;
                  return;
               }

               var135 = var41;
               var142 = var2;
            }

            while(true) {
               var147 = var135[var142];
               switch (var2 % 5) {
                  case 0:
                     var152 = 70;
                     break;
                  case 1:
                     var152 = 110;
                     break;
                  case 2:
                     var152 = 90;
                     break;
                  case 3:
                     var152 = 42;
                     break;
                  default:
                     var152 = 64;
               }

               var135[var142] = (char)(var147 ^ var152);
               ++var2;
               if (var44 == 0) {
                  var142 = var44;
                  var135 = var49;
               } else {
                  if (var44 <= var2) {
                     var10000[3] = (new String(var49)).intern();
                     a = var10000;
                     return;
                  }

                  var135 = var49;
                  var142 = var2;
               }
            }
         }

         var95 = var13;
         var142 = var2;
         var147 = var13[var2];
         switch (var2 % 5) {
            case 0:
               var152 = 70;
               break;
            case 1:
               var152 = 110;
               break;
            case 2:
               var152 = 90;
               break;
            case 3:
               var152 = 42;
               break;
            default:
               var152 = 64;
         }
      }

      while(true) {
         var95[var142] = (char)(var147 ^ var152);
         ++var2;
         if (var16 == 0) {
            var142 = var16;
            var95 = var49;
            var147 = var49[var16];
            switch (var2 % 5) {
               case 0:
                  var152 = 70;
                  break;
               case 1:
                  var152 = 110;
                  break;
               case 2:
                  var152 = 90;
                  break;
               case 3:
                  var152 = 42;
                  break;
               default:
                  var152 = 64;
            }
         } else {
            if (var16 <= var2) {
               label79: {
                  var10000[1] = (new String(var49)).intern();
                  char[] var20 = "6\u001c3I%".toCharArray();
                  int var102 = var20.length;
                  var2 = 0;
                  var49 = var20;
                  int var23 = var102;
                  char[] var105;
                  if (var102 <= 1) {
                     var105 = var20;
                     var142 = var2;
                  } else {
                     var49 = var20;
                     var23 = var102;
                     if (var102 <= var2) {
                        break label79;
                     }

                     var105 = var20;
                     var142 = var2;
                  }

                  while(true) {
                     var147 = var105[var142];
                     switch (var2 % 5) {
                        case 0:
                           var152 = 70;
                           break;
                        case 1:
                           var152 = 110;
                           break;
                        case 2:
                           var152 = 90;
                           break;
                        case 3:
                           var152 = 42;
                           break;
                        default:
                           var152 = 64;
                     }

                     var105[var142] = (char)(var147 ^ var152);
                     ++var2;
                     if (var23 == 0) {
                        var142 = var23;
                        var105 = var49;
                     } else {
                        if (var23 <= var2) {
                           break;
                        }

                        var105 = var49;
                        var142 = var2;
                     }
                  }
               }

               var10000[2] = (new String(var49)).intern();
               char[] var27 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
               int var112 = var27.length;
               var2 = 0;
               var49 = var27;
               int var30 = var112;
               char[] var115;
               if (var112 <= 1) {
                  var115 = var27;
                  var142 = var2;
               } else {
                  var49 = var27;
                  var30 = var112;
                  if (var112 <= var2) {
                     var10000[3] = (new String(var27)).intern();
                     a = var10000;
                     return;
                  }

                  var115 = var27;
                  var142 = var2;
               }

               while(true) {
                  var147 = var115[var142];
                  switch (var2 % 5) {
                     case 0:
                        var152 = 70;
                        break;
                     case 1:
                        var152 = 110;
                        break;
                     case 2:
                        var152 = 90;
                        break;
                     case 3:
                        var152 = 42;
                        break;
                     default:
                        var152 = 64;
                  }

                  var115[var142] = (char)(var147 ^ var152);
                  ++var2;
                  if (var30 == 0) {
                     var142 = var30;
                     var115 = var49;
                  } else {
                     if (var30 <= var2) {
                        var10000[3] = (new String(var49)).intern();
                        a = var10000;
                        return;
                     }

                     var115 = var49;
                     var142 = var2;
                  }
               }
            }

            var95 = var49;
            var142 = var2;
            var147 = var49[var2];
            switch (var2 % 5) {
               case 0:
                  var152 = 70;
                  break;
               case 1:
                  var152 = 110;
                  break;
               case 2:
                  var152 = 90;
                  break;
               case 3:
                  var152 = 42;
                  break;
               default:
                  var152 = 64;
            }
         }
      }
   }
}
