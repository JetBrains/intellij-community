import java.util.LinkedHashMap;
import java.util.Map;

public class s {
   private final String a;
   private Map<String, String> b = new LinkedHashMap();
   private Throwable c;
   public static int d;
   private static final String[] e;

   public s(String var1) {
      this.a = var1;
   }

   public s a(Throwable var1) {
      this.c = var1;
      return this;
   }

   public s a(String var1, Object var2) {
      try {
         if (var2 != null) {
            this.b.put(var1, var2.toString());
         }

         return this;
      } catch (a_ var3) {
         throw var3;
      }
   }

   public a9 a() {
      return r.b(this.b());
   }

   protected p b() {
      // $FF: Couldn't be decompiled
   }

   protected static String b(Throwable var0) {
      String var1 = "-";
      if (var0 != null) {
         StackTraceElement[] var2 = var0.getStackTrace();
         if (var2.length > 0) {
            var1 = var2[0].getFileName() + ":" + var2[0].getLineNumber() + "[" + var2[0].getClassName() + "." + var2[0].getMethodName() + "]";
         }
      } else {
         StackTraceElement[] var3 = Thread.currentThread().getStackTrace();
         if (var3.length > 5) {
            var1 = var3[5].getFileName() + ":" + var3[5].getLineNumber() + "[" + var3[5].getClassName() + "." + var3[5].getMethodName() + "]";
         }
      }

      return var1;
   }

   static {
      String[] var10000 = new String[5];
      char[] var10003 = "][BX\u0014\u0014\u000bsI\u0018\u001fA'".toCharArray();
      int var10005 = var10003.length;
      int var1 = 0;
      char[] var105 = var10003;
      int var16 = var10005;
      char[] var183;
      int var10006;
      char var10007;
      byte var10008;
      if (var10005 <= 1) {
         var183 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch (var1 % 5) {
            case 0:
               var10008 = 113;
               break;
            case 1:
               var10008 = 123;
               break;
            case 2:
               var10008 = 7;
               break;
            case 3:
               var10008 = 32;
               break;
            default:
               var10008 = 119;
         }
      } else {
         var105 = var10003;
         var16 = var10005;
         if (var10005 <= var1) {
            label316: {
               var10000[0] = (new String(var10003)).intern();
               char[] var62 = "\u001e\tnG\u001e\u001f".toCharArray();
               int var250 = var62.length;
               var1 = 0;
               var105 = var62;
               int var65 = var250;
               char[] var253;
               if (var250 <= 1) {
                  var253 = var62;
                  var10006 = var1;
               } else {
                  var105 = var62;
                  var65 = var250;
                  if (var250 <= var1) {
                     break label316;
                  }

                  var253 = var62;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var253[var10006];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 113;
                        break;
                     case 1:
                        var10008 = 123;
                        break;
                     case 2:
                        var10008 = 7;
                        break;
                     case 3:
                        var10008 = 32;
                        break;
                     default:
                        var10008 = 119;
                  }

                  var253[var10006] = (char)(var10007 ^ var10008);
                  ++var1;
                  if (var65 == 0) {
                     var10006 = var65;
                     var253 = var105;
                  } else {
                     if (var65 <= var1) {
                        break;
                     }

                     var253 = var105;
                     var10006 = var1;
                  }
               }
            }

            var10000[1] = (new String(var105)).intern();
            char[] var69 = "K[".toCharArray();
            int var260 = var69.length;
            var1 = 0;
            var105 = var69;
            int var72 = var260;
            char[] var263;
            if (var260 <= 1) {
               var263 = var69;
               var10006 = var1;
               var10007 = var69[var1];
               switch (var1 % 5) {
                  case 0:
                     var10008 = 113;
                     break;
                  case 1:
                     var10008 = 123;
                     break;
                  case 2:
                     var10008 = 7;
                     break;
                  case 3:
                     var10008 = 32;
                     break;
                  default:
                     var10008 = 119;
               }
            } else {
               var105 = var69;
               var72 = var260;
               if (var260 <= var1) {
                  label384: {
                     var10000[2] = (new String(var69)).intern();
                     char[] var90 = "QF'".toCharArray();
                     int var290 = var90.length;
                     var1 = 0;
                     var105 = var90;
                     int var93 = var290;
                     char[] var293;
                     if (var290 <= 1) {
                        var293 = var90;
                        var10006 = var1;
                     } else {
                        var105 = var90;
                        var93 = var290;
                        if (var290 <= var1) {
                           break label384;
                        }

                        var293 = var90;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var293[var10006];
                        switch (var1 % 5) {
                           case 0:
                              var10008 = 113;
                              break;
                           case 1:
                              var10008 = 123;
                              break;
                           case 2:
                              var10008 = 7;
                              break;
                           case 3:
                              var10008 = 32;
                              break;
                           default:
                              var10008 = 119;
                        }

                        var293[var10006] = (char)(var10007 ^ var10008);
                        ++var1;
                        if (var93 == 0) {
                           var10006 = var93;
                           var293 = var105;
                        } else {
                           if (var93 <= var1) {
                              break;
                           }

                           var293 = var105;
                           var10006 = var1;
                        }
                     }
                  }

                  var10000[3] = (new String(var105)).intern();
                  char[] var97 = "][".toCharArray();
                  int var300 = var97.length;
                  var1 = 0;
                  var105 = var97;
                  int var100 = var300;
                  char[] var303;
                  if (var300 <= 1) {
                     var303 = var97;
                     var10006 = var1;
                  } else {
                     var105 = var97;
                     var100 = var300;
                     if (var300 <= var1) {
                        var10000[4] = (new String(var97)).intern();
                        e = var10000;
                        return;
                     }

                     var303 = var97;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var303[var10006];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 113;
                           break;
                        case 1:
                           var10008 = 123;
                           break;
                        case 2:
                           var10008 = 7;
                           break;
                        case 3:
                           var10008 = 32;
                           break;
                        default:
                           var10008 = 119;
                     }

                     var303[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var100 == 0) {
                        var10006 = var100;
                        var303 = var105;
                     } else {
                        if (var100 <= var1) {
                           var10000[4] = (new String(var105)).intern();
                           e = var10000;
                           return;
                        }

                        var303 = var105;
                        var10006 = var1;
                     }
                  }
               }

               var263 = var69;
               var10006 = var1;
               var10007 = var69[var1];
               switch (var1 % 5) {
                  case 0:
                     var10008 = 113;
                     break;
                  case 1:
                     var10008 = 123;
                     break;
                  case 2:
                     var10008 = 7;
                     break;
                  case 3:
                     var10008 = 32;
                     break;
                  default:
                     var10008 = 119;
               }
            }

            while(true) {
               var263[var10006] = (char)(var10007 ^ var10008);
               ++var1;
               if (var72 == 0) {
                  var10006 = var72;
                  var263 = var105;
                  var10007 = var105[var72];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 113;
                        break;
                     case 1:
                        var10008 = 123;
                        break;
                     case 2:
                        var10008 = 7;
                        break;
                     case 3:
                        var10008 = 32;
                        break;
                     default:
                        var10008 = 119;
                  }
               } else {
                  if (var72 <= var1) {
                     label492: {
                        var10000[2] = (new String(var105)).intern();
                        char[] var76 = "QF'".toCharArray();
                        int var270 = var76.length;
                        var1 = 0;
                        var105 = var76;
                        int var79 = var270;
                        char[] var273;
                        if (var270 <= 1) {
                           var273 = var76;
                           var10006 = var1;
                        } else {
                           var105 = var76;
                           var79 = var270;
                           if (var270 <= var1) {
                              break label492;
                           }

                           var273 = var76;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var273[var10006];
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 113;
                                 break;
                              case 1:
                                 var10008 = 123;
                                 break;
                              case 2:
                                 var10008 = 7;
                                 break;
                              case 3:
                                 var10008 = 32;
                                 break;
                              default:
                                 var10008 = 119;
                           }

                           var273[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var79 == 0) {
                              var10006 = var79;
                              var273 = var105;
                           } else {
                              if (var79 <= var1) {
                                 break;
                              }

                              var273 = var105;
                              var10006 = var1;
                           }
                        }
                     }

                     var10000[3] = (new String(var105)).intern();
                     char[] var83 = "][".toCharArray();
                     int var280 = var83.length;
                     var1 = 0;
                     var105 = var83;
                     int var86 = var280;
                     char[] var283;
                     if (var280 <= 1) {
                        var283 = var83;
                        var10006 = var1;
                     } else {
                        var105 = var83;
                        var86 = var280;
                        if (var280 <= var1) {
                           var10000[4] = (new String(var83)).intern();
                           e = var10000;
                           return;
                        }

                        var283 = var83;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var283[var10006];
                        switch (var1 % 5) {
                           case 0:
                              var10008 = 113;
                              break;
                           case 1:
                              var10008 = 123;
                              break;
                           case 2:
                              var10008 = 7;
                              break;
                           case 3:
                              var10008 = 32;
                              break;
                           default:
                              var10008 = 119;
                        }

                        var283[var10006] = (char)(var10007 ^ var10008);
                        ++var1;
                        if (var86 == 0) {
                           var10006 = var86;
                           var283 = var105;
                        } else {
                           if (var86 <= var1) {
                              var10000[4] = (new String(var105)).intern();
                              e = var10000;
                              return;
                           }

                           var283 = var105;
                           var10006 = var1;
                        }
                     }
                  }

                  var263 = var105;
                  var10006 = var1;
                  var10007 = var105[var1];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 113;
                        break;
                     case 1:
                        var10008 = 123;
                        break;
                     case 2:
                        var10008 = 7;
                        break;
                     case 3:
                        var10008 = 32;
                        break;
                     default:
                        var10008 = 119;
                  }
               }
            }
         }

         var183 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch (var1 % 5) {
            case 0:
               var10008 = 113;
               break;
            case 1:
               var10008 = 123;
               break;
            case 2:
               var10008 = 7;
               break;
            case 3:
               var10008 = 32;
               break;
            default:
               var10008 = 119;
         }
      }

      while(true) {
         var183[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var16 == 0) {
            var10006 = var16;
            var183 = var105;
            var10007 = var105[var16];
            switch (var1 % 5) {
               case 0:
                  var10008 = 113;
                  break;
               case 1:
                  var10008 = 123;
                  break;
               case 2:
                  var10008 = 7;
                  break;
               case 3:
                  var10008 = 32;
                  break;
               default:
                  var10008 = 119;
            }
         } else {
            if (var16 <= var1) {
               label129: {
                  var10000[0] = (new String(var105)).intern();
                  char[] var20 = "\u001e\tnG\u001e\u001f".toCharArray();
                  int var190 = var20.length;
                  var1 = 0;
                  var105 = var20;
                  int var23 = var190;
                  char[] var193;
                  if (var190 <= 1) {
                     var193 = var20;
                     var10006 = var1;
                  } else {
                     var105 = var20;
                     var23 = var190;
                     if (var190 <= var1) {
                        break label129;
                     }

                     var193 = var20;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var193[var10006];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 113;
                           break;
                        case 1:
                           var10008 = 123;
                           break;
                        case 2:
                           var10008 = 7;
                           break;
                        case 3:
                           var10008 = 32;
                           break;
                        default:
                           var10008 = 119;
                     }

                     var193[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var23 == 0) {
                        var10006 = var23;
                        var193 = var105;
                     } else {
                        if (var23 <= var1) {
                           break;
                        }

                        var193 = var105;
                        var10006 = var1;
                     }
                  }
               }

               var10000[1] = (new String(var105)).intern();
               char[] var27 = "K[".toCharArray();
               int var200 = var27.length;
               var1 = 0;
               var105 = var27;
               int var30 = var200;
               char[] var203;
               if (var200 <= 1) {
                  var203 = var27;
                  var10006 = var1;
                  var10007 = var27[var1];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 113;
                        break;
                     case 1:
                        var10008 = 123;
                        break;
                     case 2:
                        var10008 = 7;
                        break;
                     case 3:
                        var10008 = 32;
                        break;
                     default:
                        var10008 = 119;
                  }
               } else {
                  var105 = var27;
                  var30 = var200;
                  if (var200 <= var1) {
                     label173: {
                        var10000[2] = (new String(var27)).intern();
                        char[] var48 = "QF'".toCharArray();
                        int var230 = var48.length;
                        var1 = 0;
                        var105 = var48;
                        int var51 = var230;
                        char[] var233;
                        if (var230 <= 1) {
                           var233 = var48;
                           var10006 = var1;
                        } else {
                           var105 = var48;
                           var51 = var230;
                           if (var230 <= var1) {
                              break label173;
                           }

                           var233 = var48;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var233[var10006];
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 113;
                                 break;
                              case 1:
                                 var10008 = 123;
                                 break;
                              case 2:
                                 var10008 = 7;
                                 break;
                              case 3:
                                 var10008 = 32;
                                 break;
                              default:
                                 var10008 = 119;
                           }

                           var233[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var51 == 0) {
                              var10006 = var51;
                              var233 = var105;
                           } else {
                              if (var51 <= var1) {
                                 break;
                              }

                              var233 = var105;
                              var10006 = var1;
                           }
                        }
                     }

                     var10000[3] = (new String(var105)).intern();
                     char[] var55 = "][".toCharArray();
                     int var240 = var55.length;
                     var1 = 0;
                     var105 = var55;
                     int var58 = var240;
                     char[] var243;
                     if (var240 <= 1) {
                        var243 = var55;
                        var10006 = var1;
                     } else {
                        var105 = var55;
                        var58 = var240;
                        if (var240 <= var1) {
                           var10000[4] = (new String(var55)).intern();
                           e = var10000;
                           return;
                        }

                        var243 = var55;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var243[var10006];
                        switch (var1 % 5) {
                           case 0:
                              var10008 = 113;
                              break;
                           case 1:
                              var10008 = 123;
                              break;
                           case 2:
                              var10008 = 7;
                              break;
                           case 3:
                              var10008 = 32;
                              break;
                           default:
                              var10008 = 119;
                        }

                        var243[var10006] = (char)(var10007 ^ var10008);
                        ++var1;
                        if (var58 == 0) {
                           var10006 = var58;
                           var243 = var105;
                        } else {
                           if (var58 <= var1) {
                              var10000[4] = (new String(var105)).intern();
                              e = var10000;
                              return;
                           }

                           var243 = var105;
                           var10006 = var1;
                        }
                     }
                  }

                  var203 = var27;
                  var10006 = var1;
                  var10007 = var27[var1];
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 113;
                        break;
                     case 1:
                        var10008 = 123;
                        break;
                     case 2:
                        var10008 = 7;
                        break;
                     case 3:
                        var10008 = 32;
                        break;
                     default:
                        var10008 = 119;
                  }
               }

               while(true) {
                  var203[var10006] = (char)(var10007 ^ var10008);
                  ++var1;
                  if (var30 == 0) {
                     var10006 = var30;
                     var203 = var105;
                     var10007 = var105[var30];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 113;
                           break;
                        case 1:
                           var10008 = 123;
                           break;
                        case 2:
                           var10008 = 7;
                           break;
                        case 3:
                           var10008 = 32;
                           break;
                        default:
                           var10008 = 119;
                     }
                  } else {
                     if (var30 <= var1) {
                        label93: {
                           var10000[2] = (new String(var105)).intern();
                           char[] var34 = "QF'".toCharArray();
                           int var210 = var34.length;
                           var1 = 0;
                           var105 = var34;
                           int var37 = var210;
                           char[] var213;
                           if (var210 <= 1) {
                              var213 = var34;
                              var10006 = var1;
                           } else {
                              var105 = var34;
                              var37 = var210;
                              if (var210 <= var1) {
                                 break label93;
                              }

                              var213 = var34;
                              var10006 = var1;
                           }

                           while(true) {
                              var10007 = var213[var10006];
                              switch (var1 % 5) {
                                 case 0:
                                    var10008 = 113;
                                    break;
                                 case 1:
                                    var10008 = 123;
                                    break;
                                 case 2:
                                    var10008 = 7;
                                    break;
                                 case 3:
                                    var10008 = 32;
                                    break;
                                 default:
                                    var10008 = 119;
                              }

                              var213[var10006] = (char)(var10007 ^ var10008);
                              ++var1;
                              if (var37 == 0) {
                                 var10006 = var37;
                                 var213 = var105;
                              } else {
                                 if (var37 <= var1) {
                                    break;
                                 }

                                 var213 = var105;
                                 var10006 = var1;
                              }
                           }
                        }

                        var10000[3] = (new String(var105)).intern();
                        char[] var41 = "][".toCharArray();
                        int var220 = var41.length;
                        var1 = 0;
                        var105 = var41;
                        int var44 = var220;
                        char[] var223;
                        if (var220 <= 1) {
                           var223 = var41;
                           var10006 = var1;
                        } else {
                           var105 = var41;
                           var44 = var220;
                           if (var220 <= var1) {
                              var10000[4] = (new String(var41)).intern();
                              e = var10000;
                              return;
                           }

                           var223 = var41;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var223[var10006];
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 113;
                                 break;
                              case 1:
                                 var10008 = 123;
                                 break;
                              case 2:
                                 var10008 = 7;
                                 break;
                              case 3:
                                 var10008 = 32;
                                 break;
                              default:
                                 var10008 = 119;
                           }

                           var223[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var44 == 0) {
                              var10006 = var44;
                              var223 = var105;
                           } else {
                              if (var44 <= var1) {
                                 var10000[4] = (new String(var105)).intern();
                                 e = var10000;
                                 return;
                              }

                              var223 = var105;
                              var10006 = var1;
                           }
                        }
                     }

                     var203 = var105;
                     var10006 = var1;
                     var10007 = var105[var1];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 113;
                           break;
                        case 1:
                           var10008 = 123;
                           break;
                        case 2:
                           var10008 = 7;
                           break;
                        case 3:
                           var10008 = 32;
                           break;
                        default:
                           var10008 = 119;
                     }
                  }
               }
            }

            var183 = var105;
            var10006 = var1;
            var10007 = var105[var1];
            switch (var1 % 5) {
               case 0:
                  var10008 = 113;
                  break;
               case 1:
                  var10008 = 123;
                  break;
               case 2:
                  var10008 = 7;
                  break;
               case 3:
                  var10008 = 32;
                  break;
               default:
                  var10008 = 119;
            }
         }
      }
   }
}
