import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class a5 {
   private static y<ai> a;
   private static final String[] b;

   public static void main(String[] var0) throws Exception {
      t.a();

      while(true) {
         System.out.println(b[1]);
         Thread.sleep(TimeUnit.MILLISECONDS.convert(60L, TimeUnit.SECONDS));
         a();
         System.out.println(b[0]);
      }
   }

   private static void a() {
      boolean var2 = a7.b;
      Iterator var0 = ((ai)a.a()).a().iterator();

      while(var0.hasNext()) {
         al var1 = (al)var0.next();
         System.out.println(var1.a().a() + b[3] + var1.a().b() + "\n" + DecimalFormat.getNumberInstance().format(var1.d()) + " " + var1.a().c() + b[4] + DecimalFormat.getNumberInstance().format(var1.e()) + " " + var1.a().c() + b[2] + DecimalFormat.getNumberInstance().format(var1.f()) + " " + var1.a().c() + "\n");
         if (var2) {
            break;
         }
      }

   }

   static {
      String[] var10000 = new String[5];
      char[] var10003 = "`\u001dY>>`\u001dY>>`\u001dY>>`\u001dY>>`\u001dY>>`\u001dY>>`\u001dY>".toCharArray();
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
            var10008 = 77;
            break;
         case 1:
            var10008 = 48;
            break;
         case 2:
            var10008 = 116;
            break;
         case 3:
            var10008 = 19;
            break;
         default:
            var10008 = 19;
         }
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label316: {
               var10000[0] = (new String(var10003)).intern();
               var10003 = "\u001aQ\u001dgz#WT|}(\u0010\u0019z}8D\u0011==c".toCharArray();
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
                     break label316;
                  }

                  var4 = var10003;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var4[var10006];
                  switch(var1 % 5) {
                  case 0:
                     var10008 = 77;
                     break;
                  case 1:
                     var10008 = 48;
                     break;
                  case 2:
                     var10008 = 116;
                     break;
                  case 3:
                     var10008 = 19;
                     break;
                  default:
                     var10008 = 19;
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
            var10003 = "a\u00105etm\u0002@{)m".toCharArray();
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
                  var10008 = 77;
                  break;
               case 1:
                  var10008 = 48;
                  break;
               case 2:
                  var10008 = 116;
                  break;
               case 3:
                  var10008 = 19;
                  break;
               default:
                  var10008 = 19;
               }
            } else {
               var10004 = var10003;
               var2 = var10005;
               if (var10005 <= var1) {
                  label384: {
                     var10000[2] = (new String(var10003)).intern();
                     var10003 = "m\u001dT".toCharArray();
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
                           break label384;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
                        case 0:
                           var10008 = 77;
                           break;
                        case 1:
                           var10008 = 48;
                           break;
                        case 2:
                           var10008 = 116;
                           break;
                        case 3:
                           var10008 = 19;
                           break;
                        default:
                           var10008 = 19;
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

                  var10000[3] = (new String(var10004)).intern();
                  var10003 = "a\u00105etc\u0010G#~$^N3".toCharArray();
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
                        var10000[4] = (new String(var10003)).intern();
                        b = var10000;
                        a = y.a(ai.class);
                        return;
                     }

                     var4 = var10003;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var4[var10006];
                     switch(var1 % 5) {
                     case 0:
                        var10008 = 77;
                        break;
                     case 1:
                        var10008 = 48;
                        break;
                     case 2:
                        var10008 = 116;
                        break;
                     case 3:
                        var10008 = 19;
                        break;
                     default:
                        var10008 = 19;
                     }

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[4] = (new String(var10004)).intern();
                           b = var10000;
                           a = y.a(ai.class);
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
                  var10008 = 77;
                  break;
               case 1:
                  var10008 = 48;
                  break;
               case 2:
                  var10008 = 116;
                  break;
               case 3:
                  var10008 = 19;
                  break;
               default:
                  var10008 = 19;
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
                        var10008 = 77;
                        break;
                     case 1:
                        var10008 = 48;
                        break;
                     case 2:
                        var10008 = 116;
                        break;
                     case 3:
                        var10008 = 19;
                        break;
                     default:
                        var10008 = 19;
                     }
                  } else {
                     if (var2 <= var1) {
                        label492: {
                           var10000[2] = (new String(var10004)).intern();
                           var10003 = "m\u001dT".toCharArray();
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
                                 break label492;
                              }

                              var4 = var10003;
                              var10006 = var1;
                           }

                           while(true) {
                              var10007 = var4[var10006];
                              switch(var1 % 5) {
                              case 0:
                                 var10008 = 77;
                                 break;
                              case 1:
                                 var10008 = 48;
                                 break;
                              case 2:
                                 var10008 = 116;
                                 break;
                              case 3:
                                 var10008 = 19;
                                 break;
                              default:
                                 var10008 = 19;
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

                        var10000[3] = (new String(var10004)).intern();
                        var10003 = "a\u00105etc\u0010G#~$^N3".toCharArray();
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
                              var10000[4] = (new String(var10003)).intern();
                              b = var10000;
                              a = y.a(ai.class);
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch(var1 % 5) {
                           case 0:
                              var10008 = 77;
                              break;
                           case 1:
                              var10008 = 48;
                              break;
                           case 2:
                              var10008 = 116;
                              break;
                           case 3:
                              var10008 = 19;
                              break;
                           default:
                              var10008 = 19;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[4] = (new String(var10004)).intern();
                                 b = var10000;
                                 a = y.a(ai.class);
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
                        var10008 = 77;
                        break;
                     case 1:
                        var10008 = 48;
                        break;
                     case 2:
                        var10008 = 116;
                        break;
                     case 3:
                        var10008 = 19;
                        break;
                     default:
                        var10008 = 19;
                     }
                  }
               }
            }
         }

         var4 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch(var1 % 5) {
         case 0:
            var10008 = 77;
            break;
         case 1:
            var10008 = 48;
            break;
         case 2:
            var10008 = 116;
            break;
         case 3:
            var10008 = 19;
            break;
         default:
            var10008 = 19;
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
                  var10008 = 77;
                  break;
               case 1:
                  var10008 = 48;
                  break;
               case 2:
                  var10008 = 116;
                  break;
               case 3:
                  var10008 = 19;
                  break;
               default:
                  var10008 = 19;
               }
            } else {
               if (var2 <= var1) {
                  label129: {
                     var10000[0] = (new String(var10004)).intern();
                     var10003 = "\u001aQ\u001dgz#WT|}(\u0010\u0019z}8D\u0011==c".toCharArray();
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
                           break label129;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
                        case 0:
                           var10008 = 77;
                           break;
                        case 1:
                           var10008 = 48;
                           break;
                        case 2:
                           var10008 = 116;
                           break;
                        case 3:
                           var10008 = 19;
                           break;
                        default:
                           var10008 = 19;
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
                  var10003 = "a\u00105etm\u0002@{)m".toCharArray();
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
                        var10008 = 77;
                        break;
                     case 1:
                        var10008 = 48;
                        break;
                     case 2:
                        var10008 = 116;
                        break;
                     case 3:
                        var10008 = 19;
                        break;
                     default:
                        var10008 = 19;
                     }
                  } else {
                     var10004 = var10003;
                     var2 = var10005;
                     if (var10005 <= var1) {
                        label173: {
                           var10000[2] = (new String(var10003)).intern();
                           var10003 = "m\u001dT".toCharArray();
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
                                 break label173;
                              }

                              var4 = var10003;
                              var10006 = var1;
                           }

                           while(true) {
                              var10007 = var4[var10006];
                              switch(var1 % 5) {
                              case 0:
                                 var10008 = 77;
                                 break;
                              case 1:
                                 var10008 = 48;
                                 break;
                              case 2:
                                 var10008 = 116;
                                 break;
                              case 3:
                                 var10008 = 19;
                                 break;
                              default:
                                 var10008 = 19;
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

                        var10000[3] = (new String(var10004)).intern();
                        var10003 = "a\u00105etc\u0010G#~$^N3".toCharArray();
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
                              var10000[4] = (new String(var10003)).intern();
                              b = var10000;
                              a = y.a(ai.class);
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch(var1 % 5) {
                           case 0:
                              var10008 = 77;
                              break;
                           case 1:
                              var10008 = 48;
                              break;
                           case 2:
                              var10008 = 116;
                              break;
                           case 3:
                              var10008 = 19;
                              break;
                           default:
                              var10008 = 19;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[4] = (new String(var10004)).intern();
                                 b = var10000;
                                 a = y.a(ai.class);
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
                        var10008 = 77;
                        break;
                     case 1:
                        var10008 = 48;
                        break;
                     case 2:
                        var10008 = 116;
                        break;
                     case 3:
                        var10008 = 19;
                        break;
                     default:
                        var10008 = 19;
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
                              var10008 = 77;
                              break;
                           case 1:
                              var10008 = 48;
                              break;
                           case 2:
                              var10008 = 116;
                              break;
                           case 3:
                              var10008 = 19;
                              break;
                           default:
                              var10008 = 19;
                           }
                        } else {
                           if (var2 <= var1) {
                              label93: {
                                 var10000[2] = (new String(var10004)).intern();
                                 var10003 = "m\u001dT".toCharArray();
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
                                       break label93;
                                    }

                                    var4 = var10003;
                                    var10006 = var1;
                                 }

                                 while(true) {
                                    var10007 = var4[var10006];
                                    switch(var1 % 5) {
                                    case 0:
                                       var10008 = 77;
                                       break;
                                    case 1:
                                       var10008 = 48;
                                       break;
                                    case 2:
                                       var10008 = 116;
                                       break;
                                    case 3:
                                       var10008 = 19;
                                       break;
                                    default:
                                       var10008 = 19;
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

                              var10000[3] = (new String(var10004)).intern();
                              var10003 = "a\u00105etc\u0010G#~$^N3".toCharArray();
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
                                    var10000[4] = (new String(var10003)).intern();
                                    b = var10000;
                                    a = y.a(ai.class);
                                    return;
                                 }

                                 var4 = var10003;
                                 var10006 = var1;
                              }

                              while(true) {
                                 var10007 = var4[var10006];
                                 switch(var1 % 5) {
                                 case 0:
                                    var10008 = 77;
                                    break;
                                 case 1:
                                    var10008 = 48;
                                    break;
                                 case 2:
                                    var10008 = 116;
                                    break;
                                 case 3:
                                    var10008 = 19;
                                    break;
                                 default:
                                    var10008 = 19;
                                 }

                                 var4[var10006] = (char)(var10007 ^ var10008);
                                 ++var1;
                                 if (var2 == 0) {
                                    var10006 = var2;
                                    var4 = var10004;
                                 } else {
                                    if (var2 <= var1) {
                                       var10000[4] = (new String(var10004)).intern();
                                       b = var10000;
                                       a = y.a(ai.class);
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
                              var10008 = 77;
                              break;
                           case 1:
                              var10008 = 48;
                              break;
                           case 2:
                              var10008 = 116;
                              break;
                           case 3:
                              var10008 = 19;
                              break;
                           default:
                              var10008 = 19;
                           }
                        }
                     }
                  }
               }

               var4 = var10004;
               var10006 = var1;
               var10007 = var10004[var1];
               switch(var1 % 5) {
               case 0:
                  var10008 = 77;
                  break;
               case 1:
                  var10008 = 48;
                  break;
               case 2:
                  var10008 = 116;
                  break;
               case 3:
                  var10008 = 19;
                  break;
               default:
                  var10008 = 19;
               }
            }
         }
      }
   }
}
