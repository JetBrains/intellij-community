import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@aa(
   a = {am.class}
)
public class an implements am {
   private OperatingSystemMXBean a = ManagementFactory.getOperatingSystemMXBean();
   private MemoryMXBean b = ManagementFactory.getMemoryMXBean();
   private List<MemoryPoolMXBean> c = ManagementFactory.getMemoryPoolMXBeans();
   private ThreadMXBean d = ManagementFactory.getThreadMXBean();
   private List<GarbageCollectorMXBean> e = ManagementFactory.getGarbageCollectorMXBeans();
   private Map<Long, ao> f = Collections.synchronizedMap(new TreeMap());
   private ak g;
   private List<aw> h;
   private ak i;
   private ak j;
   public static boolean k;
   private static final String[] l;

   public an() {
      this.g = new at(this, l[2], l[1]);
      this.i = new au(this, l[0], l[5]);
      this.j = new av(this, l[4], l[3]);
   }

   private List<aw> a() {
      boolean var4 = k;

      List var10000;
      label45: {
         try {
            var10000 = this.h;
            if (var4) {
               return var10000;
            }

            if (var10000 != null) {
               break label45;
            }
         } catch (a_ var6) {
            throw var6;
         }

         ArrayList var1 = new ArrayList();
         Iterator var2 = this.c.iterator();

         while(var2.hasNext()) {
            MemoryPoolMXBean var3 = (MemoryPoolMXBean)var2.next();

            try {
               var1.add(new aw(this, var3));
               if (var4) {
                  break label45;
               }

               if (var4) {
                  break;
               }
            } catch (a_ var5) {
               throw var5;
            }
         }

         this.h = var1;
      }

      var10000 = this.h;
      return var10000;
   }

   public void a(k<ak> var1) {
      var1.a((Object)this.g);
      var1.a((Collection)this.a());
      var1.a((Object)this.i);
      var1.a((Object)this.j);
   }

   public List<ao> b() {
      ArrayList var1 = new ArrayList(this.f.values());
      Collections.sort(var1);
      return var1;
   }

   static MemoryMXBean a(an var0) {
      return var0.b;
   }

   static Map b(an var0) {
      return var0.f;
   }

   static ThreadMXBean c(an var0) {
      return var0.d;
   }

   static OperatingSystemMXBean d(an var0) {
      return var0.a;
   }

   static List e(an var0) {
      return var0.e;
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
      label304: {
         var10000 = new String[6];
         var10003 = "\u0006\b\u000e".toCharArray();
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
               break label304;
            }

            var4 = var10003;
            var10006 = var1;
         }

         while(true) {
            var10007 = var4[var10006];
            switch(var1 % 5) {
            case 0:
               var10008 = 76;
               break;
            case 1:
               var10008 = 94;
               break;
            case 2:
               var10008 = 67;
               break;
            case 3:
               var10008 = 50;
               break;
            default:
               var10008 = 117;
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
      var10003 = "\u0004;\"B".toCharArray();
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
            var10008 = 76;
            break;
         case 1:
            var10008 = 94;
            break;
         case 2:
            var10008 = 67;
            break;
         case 3:
            var10008 = 50;
            break;
         default:
            var10008 = 117;
         }
      } else {
         var10004 = var10003;
         var2 = var10005;
         if (var10005 <= var1) {
            label347: {
               var10000[1] = (new String(var10003)).intern();
               var10003 = "\u0006\b\u000e".toCharArray();
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
                     break label347;
                  }

                  var4 = var10003;
                  var10006 = var1;
               }

               while(true) {
                  var10007 = var4[var10006];
                  switch(var1 % 5) {
                  case 0:
                     var10008 = 76;
                     break;
                  case 1:
                     var10008 = 94;
                     break;
                  case 2:
                     var10008 = 67;
                     break;
                  case 3:
                     var10008 = 50;
                     break;
                  default:
                     var10008 = 117;
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
            var10003 = "\u000b\u001d".toCharArray();
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
                  var10008 = 76;
                  break;
               case 1:
                  var10008 = 94;
                  break;
               case 2:
                  var10008 = 67;
                  break;
               case 3:
                  var10008 = 50;
                  break;
               default:
                  var10008 = 117;
               }
            } else {
               var10004 = var10003;
               var2 = var10005;
               if (var10005 <= var1) {
                  label415: {
                     var10000[3] = (new String(var10003)).intern();
                     var10003 = "\u0006\b\u000e".toCharArray();
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
                           break label415;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
                        case 0:
                           var10008 = 76;
                           break;
                        case 1:
                           var10008 = 94;
                           break;
                        case 2:
                           var10008 = 67;
                           break;
                        case 3:
                           var10008 = 50;
                           break;
                        default:
                           var10008 = 117;
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

                  var10000[4] = (new String(var10004)).intern();
                  var10003 = "\u000f\u000e\u0016".toCharArray();
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
                        var10000[5] = (new String(var10003)).intern();
                        l = var10000;
                        return;
                     }

                     var4 = var10003;
                     var10006 = var1;
                  }

                  while(true) {
                     var10007 = var4[var10006];
                     switch(var1 % 5) {
                     case 0:
                        var10008 = 76;
                        break;
                     case 1:
                        var10008 = 94;
                        break;
                     case 2:
                        var10008 = 67;
                        break;
                     case 3:
                        var10008 = 50;
                        break;
                     default:
                        var10008 = 117;
                     }

                     var4[var10006] = (char)(var10007 ^ var10008);
                     ++var1;
                     if (var2 == 0) {
                        var10006 = var2;
                        var4 = var10004;
                     } else {
                        if (var2 <= var1) {
                           var10000[5] = (new String(var10004)).intern();
                           l = var10000;
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
                  var10008 = 76;
                  break;
               case 1:
                  var10008 = 94;
                  break;
               case 2:
                  var10008 = 67;
                  break;
               case 3:
                  var10008 = 50;
                  break;
               default:
                  var10008 = 117;
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
                        var10008 = 76;
                        break;
                     case 1:
                        var10008 = 94;
                        break;
                     case 2:
                        var10008 = 67;
                        break;
                     case 3:
                        var10008 = 50;
                        break;
                     default:
                        var10008 = 117;
                     }
                  } else {
                     if (var2 <= var1) {
                        label523: {
                           var10000[3] = (new String(var10004)).intern();
                           var10003 = "\u0006\b\u000e".toCharArray();
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
                                 break label523;
                              }

                              var4 = var10003;
                              var10006 = var1;
                           }

                           while(true) {
                              var10007 = var4[var10006];
                              switch(var1 % 5) {
                              case 0:
                                 var10008 = 76;
                                 break;
                              case 1:
                                 var10008 = 94;
                                 break;
                              case 2:
                                 var10008 = 67;
                                 break;
                              case 3:
                                 var10008 = 50;
                                 break;
                              default:
                                 var10008 = 117;
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

                        var10000[4] = (new String(var10004)).intern();
                        var10003 = "\u000f\u000e\u0016".toCharArray();
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
                              var10000[5] = (new String(var10003)).intern();
                              l = var10000;
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch(var1 % 5) {
                           case 0:
                              var10008 = 76;
                              break;
                           case 1:
                              var10008 = 94;
                              break;
                           case 2:
                              var10008 = 67;
                              break;
                           case 3:
                              var10008 = 50;
                              break;
                           default:
                              var10008 = 117;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[5] = (new String(var10004)).intern();
                                 l = var10000;
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
                        var10008 = 76;
                        break;
                     case 1:
                        var10008 = 94;
                        break;
                     case 2:
                        var10008 = 67;
                        break;
                     case 3:
                        var10008 = 50;
                        break;
                     default:
                        var10008 = 117;
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
            var10008 = 76;
            break;
         case 1:
            var10008 = 94;
            break;
         case 2:
            var10008 = 67;
            break;
         case 3:
            var10008 = 50;
            break;
         default:
            var10008 = 117;
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
                  var10008 = 76;
                  break;
               case 1:
                  var10008 = 94;
                  break;
               case 2:
                  var10008 = 67;
                  break;
               case 3:
                  var10008 = 50;
                  break;
               default:
                  var10008 = 117;
               }
            } else {
               if (var2 <= var1) {
                  label143: {
                     var10000[1] = (new String(var10004)).intern();
                     var10003 = "\u0006\b\u000e".toCharArray();
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
                           break label143;
                        }

                        var4 = var10003;
                        var10006 = var1;
                     }

                     while(true) {
                        var10007 = var4[var10006];
                        switch(var1 % 5) {
                        case 0:
                           var10008 = 76;
                           break;
                        case 1:
                           var10008 = 94;
                           break;
                        case 2:
                           var10008 = 67;
                           break;
                        case 3:
                           var10008 = 50;
                           break;
                        default:
                           var10008 = 117;
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
                  var10003 = "\u000b\u001d".toCharArray();
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
                        var10008 = 76;
                        break;
                     case 1:
                        var10008 = 94;
                        break;
                     case 2:
                        var10008 = 67;
                        break;
                     case 3:
                        var10008 = 50;
                        break;
                     default:
                        var10008 = 117;
                     }
                  } else {
                     var10004 = var10003;
                     var2 = var10005;
                     if (var10005 <= var1) {
                        label187: {
                           var10000[3] = (new String(var10003)).intern();
                           var10003 = "\u0006\b\u000e".toCharArray();
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
                                 break label187;
                              }

                              var4 = var10003;
                              var10006 = var1;
                           }

                           while(true) {
                              var10007 = var4[var10006];
                              switch(var1 % 5) {
                              case 0:
                                 var10008 = 76;
                                 break;
                              case 1:
                                 var10008 = 94;
                                 break;
                              case 2:
                                 var10008 = 67;
                                 break;
                              case 3:
                                 var10008 = 50;
                                 break;
                              default:
                                 var10008 = 117;
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

                        var10000[4] = (new String(var10004)).intern();
                        var10003 = "\u000f\u000e\u0016".toCharArray();
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
                              var10000[5] = (new String(var10003)).intern();
                              l = var10000;
                              return;
                           }

                           var4 = var10003;
                           var10006 = var1;
                        }

                        while(true) {
                           var10007 = var4[var10006];
                           switch(var1 % 5) {
                           case 0:
                              var10008 = 76;
                              break;
                           case 1:
                              var10008 = 94;
                              break;
                           case 2:
                              var10008 = 67;
                              break;
                           case 3:
                              var10008 = 50;
                              break;
                           default:
                              var10008 = 117;
                           }

                           var4[var10006] = (char)(var10007 ^ var10008);
                           ++var1;
                           if (var2 == 0) {
                              var10006 = var2;
                              var4 = var10004;
                           } else {
                              if (var2 <= var1) {
                                 var10000[5] = (new String(var10004)).intern();
                                 l = var10000;
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
                        var10008 = 76;
                        break;
                     case 1:
                        var10008 = 94;
                        break;
                     case 2:
                        var10008 = 67;
                        break;
                     case 3:
                        var10008 = 50;
                        break;
                     default:
                        var10008 = 117;
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
                              var10008 = 76;
                              break;
                           case 1:
                              var10008 = 94;
                              break;
                           case 2:
                              var10008 = 67;
                              break;
                           case 3:
                              var10008 = 50;
                              break;
                           default:
                              var10008 = 117;
                           }
                        } else {
                           if (var2 <= var1) {
                              label107: {
                                 var10000[3] = (new String(var10004)).intern();
                                 var10003 = "\u0006\b\u000e".toCharArray();
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
                                       break label107;
                                    }

                                    var4 = var10003;
                                    var10006 = var1;
                                 }

                                 while(true) {
                                    var10007 = var4[var10006];
                                    switch(var1 % 5) {
                                    case 0:
                                       var10008 = 76;
                                       break;
                                    case 1:
                                       var10008 = 94;
                                       break;
                                    case 2:
                                       var10008 = 67;
                                       break;
                                    case 3:
                                       var10008 = 50;
                                       break;
                                    default:
                                       var10008 = 117;
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

                              var10000[4] = (new String(var10004)).intern();
                              var10003 = "\u000f\u000e\u0016".toCharArray();
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
                                    var10000[5] = (new String(var10003)).intern();
                                    l = var10000;
                                    return;
                                 }

                                 var4 = var10003;
                                 var10006 = var1;
                              }

                              while(true) {
                                 var10007 = var4[var10006];
                                 switch(var1 % 5) {
                                 case 0:
                                    var10008 = 76;
                                    break;
                                 case 1:
                                    var10008 = 94;
                                    break;
                                 case 2:
                                    var10008 = 67;
                                    break;
                                 case 3:
                                    var10008 = 50;
                                    break;
                                 default:
                                    var10008 = 117;
                                 }

                                 var4[var10006] = (char)(var10007 ^ var10008);
                                 ++var1;
                                 if (var2 == 0) {
                                    var10006 = var2;
                                    var4 = var10004;
                                 } else {
                                    if (var2 <= var1) {
                                       var10000[5] = (new String(var10004)).intern();
                                       l = var10000;
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
                              var10008 = 76;
                              break;
                           case 1:
                              var10008 = 94;
                              break;
                           case 2:
                              var10008 = 67;
                              break;
                           case 3:
                              var10008 = 50;
                              break;
                           default:
                              var10008 = 117;
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
                  var10008 = 76;
                  break;
               case 1:
                  var10008 = 94;
                  break;
               case 2:
                  var10008 = 67;
                  break;
               case 3:
                  var10008 = 50;
                  break;
               default:
                  var10008 = 117;
               }
            }
         }
      }
   }
}
