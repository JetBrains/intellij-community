import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.ReplacedElement;
import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.layout.LayoutContext;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextReplacedElementFactory;
import org.xhtmlrenderer.render.BlockBox;

public class bb extends ITextReplacedElementFactory {
   public static boolean b;
   private static final String[] a;

   public bb(ITextOutputDevice var1) {
      super(var1);
   }

   public ReplacedElement createReplacedElement(LayoutContext param1, BlockBox param2, UserAgentCallback param3, int param4, int param5) {
      // $FF: Couldn't be decompiled
   }

   private n<Integer, Integer> a(int param1, int param2, FSImage param3) {
      // $FF: Couldn't be decompiled
   }

   static {
      String[] var10000 = new String[5];
      char[] var10003 = "u\u0003\u000e".toCharArray();
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
         switch (var1 % 5) {
            case 0:
               var10008 = 28;
               break;
            case 1:
               var10008 = 110;
               break;
            case 2:
               var10008 = 105;
               break;
            case 3:
               var10008 = 46;
               break;
            default:
               var10008 = 33;
         }
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label316: {
               var10000[0] = (new String(var10003)).intern();
               var10003 = "o\u001c\n".toCharArray();
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
                  switch (var1 % 5) {
                     case 0:
                        var10008 = 28;
                        break;
                     case 1:
                        var10008 = 110;
                        break;
                     case 2:
                        var10008 = 105;
                        break;
                     case 3:
                        var10008 = 46;
                        break;
                     default:
                        var10008 = 33;
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
            var10003 = "h\u0017\u0019K".toCharArray();
            var10005 = var10003.length;
            var1 = 0;
            var10004 = var10003;
            var2 = var10005;
            if (var10005 <= 1) {
               var4 = var10003;
               var10006 = var1;
               var10007 = var10003[var1];
               switch (var1 % 5) {
                  case 0:
                     var10008 = 28;
                     break;
                  case 1:
                     var10008 = 110;
                     break;
                  case 2:
                     var10008 = 105;
                     break;
                  case 3:
                     var10008 = 46;
                     break;
                  default:
                     var10008 = 33;
               }
            } else {
               var10004 = var10003;
               var2 = var10005;
               if (var10005 <= var1) {
                  label384: {
                     var10000[2] = (new String(var10003)).intern();
                     var10003 = "\u007f\u0001\rK\u0010.V".toCharArray();
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
                        switch (var1 % 5) {
                           case 0:
                              var10008 = 28;
                              break;
                           case 1:
                              var10008 = 110;
                              break;
                           case 2:
                              var10008 = 105;
                              break;
                           case 3:
                              var10008 = 46;
                              break;
                           default:
                              var10008 = 33;
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
                  var10003 = "o\u001c\n".toCharArray();
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
                        a = var10000;
                        return;
                     }

                     var4 = var10003;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var4[var10006];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 28;
                           break;
                        case 1:
                           var10008 = 110;
                           break;
                        case 2:
                           var10008 = 105;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 33;
                     }

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[4] = (new String(var10004)).intern();
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
               switch (var1 % 5) {
                  case 0:
                     var10008 = 28;
                     break;
                  case 1:
                     var10008 = 110;
                     break;
                  case 2:
                     var10008 = 105;
                     break;
                  case 3:
                     var10008 = 46;
                     break;
                  default:
                     var10008 = 33;
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
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 28;
                           break;
                        case 1:
                           var10008 = 110;
                           break;
                        case 2:
                           var10008 = 105;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 33;
                     }
                  } else {
                     if (var2 <= var1) {
                        label492: {
                           var10000[2] = (new String(var10004)).intern();
                           var10003 = "\u007f\u0001\rK\u0010.V".toCharArray();
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
                              switch (var1 % 5) {
                                 case 0:
                                    var10008 = 28;
                                    break;
                                 case 1:
                                    var10008 = 110;
                                    break;
                                 case 2:
                                    var10008 = 105;
                                    break;
                                 case 3:
                                    var10008 = 46;
                                    break;
                                 default:
                                    var10008 = 33;
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
                        var10003 = "o\u001c\n".toCharArray();
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
                              a = var10000;
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 28;
                                 break;
                              case 1:
                                 var10008 = 110;
                                 break;
                              case 2:
                                 var10008 = 105;
                                 break;
                              case 3:
                                 var10008 = 46;
                                 break;
                              default:
                                 var10008 = 33;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[4] = (new String(var10004)).intern();
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
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 28;
                           break;
                        case 1:
                           var10008 = 110;
                           break;
                        case 2:
                           var10008 = 105;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 33;
                     }
                  }
               }
            }
         }

         var4 = var10003;
         var10006 = var1;
         var10007 = var10003[var1];
         switch (var1 % 5) {
            case 0:
               var10008 = 28;
               break;
            case 1:
               var10008 = 110;
               break;
            case 2:
               var10008 = 105;
               break;
            case 3:
               var10008 = 46;
               break;
            default:
               var10008 = 33;
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
               switch (var1 % 5) {
                  case 0:
                     var10008 = 28;
                     break;
                  case 1:
                     var10008 = 110;
                     break;
                  case 2:
                     var10008 = 105;
                     break;
                  case 3:
                     var10008 = 46;
                     break;
                  default:
                     var10008 = 33;
               }
            } else {
               if (var2 <= var1) {
                  label129: {
                     var10000[0] = (new String(var10004)).intern();
                     var10003 = "o\u001c\n".toCharArray();
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
                        switch (var1 % 5) {
                           case 0:
                              var10008 = 28;
                              break;
                           case 1:
                              var10008 = 110;
                              break;
                           case 2:
                              var10008 = 105;
                              break;
                           case 3:
                              var10008 = 46;
                              break;
                           default:
                              var10008 = 33;
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
                  var10003 = "h\u0017\u0019K".toCharArray();
                  var10005 = var10003.length;
                  var1 = 0;
                  var10004 = var10003;
                  var2 = var10005;
                  if (var10005 <= 1) {
                     var4 = var10003;
                     var10006 = var1;
                     var10007 = var10003[var1];
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 28;
                           break;
                        case 1:
                           var10008 = 110;
                           break;
                        case 2:
                           var10008 = 105;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 33;
                     }
                  } else {
                     var10004 = var10003;
                     var2 = var10005;
                     if (var10005 <= var1) {
                        label173: {
                           var10000[2] = (new String(var10003)).intern();
                           var10003 = "\u007f\u0001\rK\u0010.V".toCharArray();
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
                              switch (var1 % 5) {
                                 case 0:
                                    var10008 = 28;
                                    break;
                                 case 1:
                                    var10008 = 110;
                                    break;
                                 case 2:
                                    var10008 = 105;
                                    break;
                                 case 3:
                                    var10008 = 46;
                                    break;
                                 default:
                                    var10008 = 33;
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
                        var10003 = "o\u001c\n".toCharArray();
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
                              a = var10000;
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 28;
                                 break;
                              case 1:
                                 var10008 = 110;
                                 break;
                              case 2:
                                 var10008 = 105;
                                 break;
                              case 3:
                                 var10008 = 46;
                                 break;
                              default:
                                 var10008 = 33;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[4] = (new String(var10004)).intern();
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
                     switch (var1 % 5) {
                        case 0:
                           var10008 = 28;
                           break;
                        case 1:
                           var10008 = 110;
                           break;
                        case 2:
                           var10008 = 105;
                           break;
                        case 3:
                           var10008 = 46;
                           break;
                        default:
                           var10008 = 33;
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
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 28;
                                 break;
                              case 1:
                                 var10008 = 110;
                                 break;
                              case 2:
                                 var10008 = 105;
                                 break;
                              case 3:
                                 var10008 = 46;
                                 break;
                              default:
                                 var10008 = 33;
                           }
                        } else {
                           if (var2 <= var1) {
                              label93: {
                                 var10000[2] = (new String(var10004)).intern();
                                 var10003 = "\u007f\u0001\rK\u0010.V".toCharArray();
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
                                    switch (var1 % 5) {
                                       case 0:
                                          var10008 = 28;
                                          break;
                                       case 1:
                                          var10008 = 110;
                                          break;
                                       case 2:
                                          var10008 = 105;
                                          break;
                                       case 3:
                                          var10008 = 46;
                                          break;
                                       default:
                                          var10008 = 33;
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
                              var10003 = "o\u001c\n".toCharArray();
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
                                    a = var10000;
                                    return;
                                 }

                                 var4 = var10003;
                                 var10006 = var1;
                              }

                              while(true) {
                                 var10007 = var4[var10006];
                                 switch (var1 % 5) {
                                    case 0:
                                       var10008 = 28;
                                       break;
                                    case 1:
                                       var10008 = 110;
                                       break;
                                    case 2:
                                       var10008 = 105;
                                       break;
                                    case 3:
                                       var10008 = 46;
                                       break;
                                    default:
                                       var10008 = 33;
                                 }

                                 var4[var10006] = (char)(var10007 ^ var10008);
                                 ++var1;
                                 if (var2 == 0) {
                                    var10006 = var2;
                                    var4 = var10004;
                                 } else {
                                    if (var2 <= var1) {
                                       var10000[4] = (new String(var10004)).intern();
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
                           switch (var1 % 5) {
                              case 0:
                                 var10008 = 28;
                                 break;
                              case 1:
                                 var10008 = 110;
                                 break;
                              case 2:
                                 var10008 = 105;
                                 break;
                              case 3:
                                 var10008 = 46;
                                 break;
                              default:
                                 var10008 = 33;
                           }
                        }
                     }
                  }
               }

               var4 = var10004;
               var10006 = var1;
               var10007 = var10004[var1];
               switch (var1 % 5) {
                  case 0:
                     var10008 = 28;
                     break;
                  case 1:
                     var10008 = 110;
                     break;
                  case 2:
                     var10008 = 105;
                     break;
                  case 3:
                     var10008 = 46;
                     break;
                  default:
                     var10008 = 33;
               }
            }
         }
      }
   }
}
