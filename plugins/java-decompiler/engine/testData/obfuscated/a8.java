final class a8 implements ay {
   private static final String[] a;

   public void a(a0 param1) {
      // $FF: Couldn't be decompiled
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
      label115: {
         var10000 = new String[4];
         var10003 = "(\u000f7O".toCharArray();
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
               break label115;
            }

            var4 = var10003;
            var10006 = var1;
         }

         while(true) {
            var10007 = var4[var10006];
            switch(var1 % 5) {
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
      var10003 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
      var10005 = var10003.length;
      var1 = 0;
      var10004 = var10003;
      var2 = var10005;
      if (var10005 <= 1) {
         var4 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch(var1 % 5) {
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
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label158: {
               var10000[1] = (new String(var10003)).intern();
               var10003 = "6\u001c3I%".toCharArray();
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
                     break label158;
                  }

                  var4 = var10003;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var4[var10006];
                  switch(var1 % 5) {
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

            var10000[2] = (new String(var10004)).intern();
            var10003 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
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
                  var10000[3] = (new String(var10003)).intern();
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

               var4[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var2 == 0) {
                  var10006 = var2;
                  var4 = var10004;
               } else {
                  if (var2 <= var1) {
                     var10000[3] = (new String(var10004)).intern();
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
            } else {
               if (var2 <= var1) {
                  label79: {
                     var10000[1] = (new String(var10004)).intern();
                     var10003 = "6\u001c3I%".toCharArray();
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
                           break label79;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
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

                  var10000[2] = (new String(var10004)).intern();
                  var10003 = "4\u000b)E54\r?Yo4\u000b)E54\r?q\u00002\u0017*O}a\u001a?Y4a3".toCharArray();
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
                        var10000[3] = (new String(var10003)).intern();
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

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[3] = (new String(var10004)).intern();
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
            }
         }
      }
   }
}
