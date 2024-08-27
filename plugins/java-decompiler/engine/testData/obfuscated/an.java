import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

         for(MemoryPoolMXBean var3 : this.c) {
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
      char[] var113;
      String[] var10000;
      label304: {
         var10000 = new String[6];
         char[] var10003 = "\u0006\b\u000e".toCharArray();
         int var10005 = var10003.length;
         int var1 = 0;
         var113 = var10003;
         int var17 = var10005;
         char[] var197;
         int var10006;
         if (var10005 <= 1) {
            var197 = var10003;
            var10006 = var1;
         } else {
            var113 = var10003;
            var17 = var10005;
            if (var10005 <= var1) {
               break label304;
            }

            var197 = var10003;
            var10006 = var1;
         }

         while(true) {
            char var10007 = var197[var10006];
            byte var10008;
            switch (var1 % 5) {
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

            var197[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var17 == 0) {
               var10006 = var17;
               var197 = var113;
            } else {
               if (var17 <= var1) {
                  break;
               }

               var197 = var113;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var113)).intern();
      char[] var21 = "\u0004;\"B".toCharArray();
      int var204 = var21.length;
      int var2 = 0;
      var113 = var21;
      int var24 = var204;
      char[] var207;
      int var334;
      char var347;
      byte var360;
      if (var204 <= 1) {
         var207 = var21;
         var334 = var2;
         var347 = var21[var2];
         switch (var2 % 5) {
            case 0:
               var360 = 76;
               break;
            case 1:
               var360 = 94;
               break;
            case 2:
               var360 = 67;
               break;
            case 3:
               var360 = 50;
               break;
            default:
               var360 = 117;
         }
      } else {
         var113 = var21;
         var24 = var204;
         if (var204 <= var2) {
            label347: {
               var10000[1] = (new String(var21)).intern();
               char[] var70 = "\u0006\b\u000e".toCharArray();
               int var274 = var70.length;
               var2 = 0;
               var113 = var70;
               int var73 = var274;
               char[] var277;
               if (var274 <= 1) {
                  var277 = var70;
                  var334 = var2;
               } else {
                  var113 = var70;
                  var73 = var274;
                  if (var274 <= var2) {
                     break label347;
                  }

                  var277 = var70;
                  var334 = var2;
               }

               while(true) {
                  var347 = var277[var334];
                  switch (var2 % 5) {
                     case 0:
                        var360 = 76;
                        break;
                     case 1:
                        var360 = 94;
                        break;
                     case 2:
                        var360 = 67;
                        break;
                     case 3:
                        var360 = 50;
                        break;
                     default:
                        var360 = 117;
                  }

                  var277[var334] = (char)(var347 ^ var360);
                  ++var2;
                  if (var73 == 0) {
                     var334 = var73;
                     var277 = var113;
                  } else {
                     if (var73 <= var2) {
                        break;
                     }

                     var277 = var113;
                     var334 = var2;
                  }
               }
            }

            var10000[2] = (new String(var113)).intern();
            char[] var77 = "\u000b\u001d".toCharArray();
            int var284 = var77.length;
            var2 = 0;
            var113 = var77;
            int var80 = var284;
            char[] var287;
            if (var284 <= 1) {
               var287 = var77;
               var334 = var2;
               var347 = var77[var2];
               switch (var2 % 5) {
                  case 0:
                     var360 = 76;
                     break;
                  case 1:
                     var360 = 94;
                     break;
                  case 2:
                     var360 = 67;
                     break;
                  case 3:
                     var360 = 50;
                     break;
                  default:
                     var360 = 117;
               }
            } else {
               var113 = var77;
               var80 = var284;
               if (var284 <= var2) {
                  label415: {
                     var10000[3] = (new String(var77)).intern();
                     char[] var98 = "\u0006\b\u000e".toCharArray();
                     int var314 = var98.length;
                     var2 = 0;
                     var113 = var98;
                     int var101 = var314;
                     char[] var317;
                     if (var314 <= 1) {
                        var317 = var98;
                        var334 = var2;
                     } else {
                        var113 = var98;
                        var101 = var314;
                        if (var314 <= var2) {
                           break label415;
                        }

                        var317 = var98;
                        var334 = var2;
                     }

                     while(true) {
                        var347 = var317[var334];
                        switch (var2 % 5) {
                           case 0:
                              var360 = 76;
                              break;
                           case 1:
                              var360 = 94;
                              break;
                           case 2:
                              var360 = 67;
                              break;
                           case 3:
                              var360 = 50;
                              break;
                           default:
                              var360 = 117;
                        }

                        var317[var334] = (char)(var347 ^ var360);
                        ++var2;
                        if (var101 == 0) {
                           var334 = var101;
                           var317 = var113;
                        } else {
                           if (var101 <= var2) {
                              break;
                           }

                           var317 = var113;
                           var334 = var2;
                        }
                     }
                  }

                  var10000[4] = (new String(var113)).intern();
                  char[] var105 = "\u000f\u000e\u0016".toCharArray();
                  int var324 = var105.length;
                  var2 = 0;
                  var113 = var105;
                  int var108 = var324;
                  char[] var327;
                  if (var324 <= 1) {
                     var327 = var105;
                     var334 = var2;
                  } else {
                     var113 = var105;
                     var108 = var324;
                     if (var324 <= var2) {
                        var10000[5] = (new String(var105)).intern();
                        l = var10000;
                        return;
                     }

                     var327 = var105;
                     var334 = var2;
                  }

                  while(true) {
                     var347 = var327[var334];
                     switch (var2 % 5) {
                        case 0:
                           var360 = 76;
                           break;
                        case 1:
                           var360 = 94;
                           break;
                        case 2:
                           var360 = 67;
                           break;
                        case 3:
                           var360 = 50;
                           break;
                        default:
                           var360 = 117;
                     }

                     var327[var334] = (char)(var347 ^ var360);
                     ++var2;
                     if (var108 == 0) {
                        var334 = var108;
                        var327 = var113;
                     } else {
                        if (var108 <= var2) {
                           var10000[5] = (new String(var113)).intern();
                           l = var10000;
                           return;
                        }

                        var327 = var113;
                        var334 = var2;
                     }
                  }
               }

               var287 = var77;
               var334 = var2;
               var347 = var77[var2];
               switch (var2 % 5) {
                  case 0:
                     var360 = 76;
                     break;
                  case 1:
                     var360 = 94;
                     break;
                  case 2:
                     var360 = 67;
                     break;
                  case 3:
                     var360 = 50;
                     break;
                  default:
                     var360 = 117;
               }
            }

            while(true) {
               var287[var334] = (char)(var347 ^ var360);
               ++var2;
               if (var80 == 0) {
                  var334 = var80;
                  var287 = var113;
                  var347 = var113[var80];
                  switch (var2 % 5) {
                     case 0:
                        var360 = 76;
                        break;
                     case 1:
                        var360 = 94;
                        break;
                     case 2:
                        var360 = 67;
                        break;
                     case 3:
                        var360 = 50;
                        break;
                     default:
                        var360 = 117;
                  }
               } else {
                  if (var80 <= var2) {
                     label523: {
                        var10000[3] = (new String(var113)).intern();
                        char[] var84 = "\u0006\b\u000e".toCharArray();
                        int var294 = var84.length;
                        var2 = 0;
                        var113 = var84;
                        int var87 = var294;
                        char[] var297;
                        if (var294 <= 1) {
                           var297 = var84;
                           var334 = var2;
                        } else {
                           var113 = var84;
                           var87 = var294;
                           if (var294 <= var2) {
                              break label523;
                           }

                           var297 = var84;
                           var334 = var2;
                        }

                        while(true) {
                           var347 = var297[var334];
                           switch (var2 % 5) {
                              case 0:
                                 var360 = 76;
                                 break;
                              case 1:
                                 var360 = 94;
                                 break;
                              case 2:
                                 var360 = 67;
                                 break;
                              case 3:
                                 var360 = 50;
                                 break;
                              default:
                                 var360 = 117;
                           }

                           var297[var334] = (char)(var347 ^ var360);
                           ++var2;
                           if (var87 == 0) {
                              var334 = var87;
                              var297 = var113;
                           } else {
                              if (var87 <= var2) {
                                 break;
                              }

                              var297 = var113;
                              var334 = var2;
                           }
                        }
                     }

                     var10000[4] = (new String(var113)).intern();
                     char[] var91 = "\u000f\u000e\u0016".toCharArray();
                     int var304 = var91.length;
                     var2 = 0;
                     var113 = var91;
                     int var94 = var304;
                     char[] var307;
                     if (var304 <= 1) {
                        var307 = var91;
                        var334 = var2;
                     } else {
                        var113 = var91;
                        var94 = var304;
                        if (var304 <= var2) {
                           var10000[5] = (new String(var91)).intern();
                           l = var10000;
                           return;
                        }

                        var307 = var91;
                        var334 = var2;
                     }

                     while(true) {
                        var347 = var307[var334];
                        switch (var2 % 5) {
                           case 0:
                              var360 = 76;
                              break;
                           case 1:
                              var360 = 94;
                              break;
                           case 2:
                              var360 = 67;
                              break;
                           case 3:
                              var360 = 50;
                              break;
                           default:
                              var360 = 117;
                        }

                        var307[var334] = (char)(var347 ^ var360);
                        ++var2;
                        if (var94 == 0) {
                           var334 = var94;
                           var307 = var113;
                        } else {
                           if (var94 <= var2) {
                              var10000[5] = (new String(var113)).intern();
                              l = var10000;
                              return;
                           }

                           var307 = var113;
                           var334 = var2;
                        }
                     }
                  }

                  var287 = var113;
                  var334 = var2;
                  var347 = var113[var2];
                  switch (var2 % 5) {
                     case 0:
                        var360 = 76;
                        break;
                     case 1:
                        var360 = 94;
                        break;
                     case 2:
                        var360 = 67;
                        break;
                     case 3:
                        var360 = 50;
                        break;
                     default:
                        var360 = 117;
                  }
               }
            }
         }

         var207 = var21;
         var334 = var2;
         var347 = var21[var2];
         switch (var2 % 5) {
            case 0:
               var360 = 76;
               break;
            case 1:
               var360 = 94;
               break;
            case 2:
               var360 = 67;
               break;
            case 3:
               var360 = 50;
               break;
            default:
               var360 = 117;
         }
      }

      while(true) {
         var207[var334] = (char)(var347 ^ var360);
         ++var2;
         if (var24 == 0) {
            var334 = var24;
            var207 = var113;
            var347 = var113[var24];
            switch (var2 % 5) {
               case 0:
                  var360 = 76;
                  break;
               case 1:
                  var360 = 94;
                  break;
               case 2:
                  var360 = 67;
                  break;
               case 3:
                  var360 = 50;
                  break;
               default:
                  var360 = 117;
            }
         } else {
            if (var24 <= var2) {
               label143: {
                  var10000[1] = (new String(var113)).intern();
                  char[] var28 = "\u0006\b\u000e".toCharArray();
                  int var214 = var28.length;
                  var2 = 0;
                  var113 = var28;
                  int var31 = var214;
                  char[] var217;
                  if (var214 <= 1) {
                     var217 = var28;
                     var334 = var2;
                  } else {
                     var113 = var28;
                     var31 = var214;
                     if (var214 <= var2) {
                        break label143;
                     }

                     var217 = var28;
                     var334 = var2;
                  }

                  while(true) {
                     var347 = var217[var334];
                     switch (var2 % 5) {
                        case 0:
                           var360 = 76;
                           break;
                        case 1:
                           var360 = 94;
                           break;
                        case 2:
                           var360 = 67;
                           break;
                        case 3:
                           var360 = 50;
                           break;
                        default:
                           var360 = 117;
                     }

                     var217[var334] = (char)(var347 ^ var360);
                     ++var2;
                     if (var31 == 0) {
                        var334 = var31;
                        var217 = var113;
                     } else {
                        if (var31 <= var2) {
                           break;
                        }

                        var217 = var113;
                        var334 = var2;
                     }
                  }
               }

               var10000[2] = (new String(var113)).intern();
               char[] var35 = "\u000b\u001d".toCharArray();
               int var224 = var35.length;
               var2 = 0;
               var113 = var35;
               int var38 = var224;
               char[] var227;
               if (var224 <= 1) {
                  var227 = var35;
                  var334 = var2;
                  var347 = var35[var2];
                  switch (var2 % 5) {
                     case 0:
                        var360 = 76;
                        break;
                     case 1:
                        var360 = 94;
                        break;
                     case 2:
                        var360 = 67;
                        break;
                     case 3:
                        var360 = 50;
                        break;
                     default:
                        var360 = 117;
                  }
               } else {
                  var113 = var35;
                  var38 = var224;
                  if (var224 <= var2) {
                     label187: {
                        var10000[3] = (new String(var35)).intern();
                        char[] var56 = "\u0006\b\u000e".toCharArray();
                        int var254 = var56.length;
                        var2 = 0;
                        var113 = var56;
                        int var59 = var254;
                        char[] var257;
                        if (var254 <= 1) {
                           var257 = var56;
                           var334 = var2;
                        } else {
                           var113 = var56;
                           var59 = var254;
                           if (var254 <= var2) {
                              break label187;
                           }

                           var257 = var56;
                           var334 = var2;
                        }

                        while(true) {
                           var347 = var257[var334];
                           switch (var2 % 5) {
                              case 0:
                                 var360 = 76;
                                 break;
                              case 1:
                                 var360 = 94;
                                 break;
                              case 2:
                                 var360 = 67;
                                 break;
                              case 3:
                                 var360 = 50;
                                 break;
                              default:
                                 var360 = 117;
                           }

                           var257[var334] = (char)(var347 ^ var360);
                           ++var2;
                           if (var59 == 0) {
                              var334 = var59;
                              var257 = var113;
                           } else {
                              if (var59 <= var2) {
                                 break;
                              }

                              var257 = var113;
                              var334 = var2;
                           }
                        }
                     }

                     var10000[4] = (new String(var113)).intern();
                     char[] var63 = "\u000f\u000e\u0016".toCharArray();
                     int var264 = var63.length;
                     var2 = 0;
                     var113 = var63;
                     int var66 = var264;
                     char[] var267;
                     if (var264 <= 1) {
                        var267 = var63;
                        var334 = var2;
                     } else {
                        var113 = var63;
                        var66 = var264;
                        if (var264 <= var2) {
                           var10000[5] = (new String(var63)).intern();
                           l = var10000;
                           return;
                        }

                        var267 = var63;
                        var334 = var2;
                     }

                     while(true) {
                        var347 = var267[var334];
                        switch (var2 % 5) {
                           case 0:
                              var360 = 76;
                              break;
                           case 1:
                              var360 = 94;
                              break;
                           case 2:
                              var360 = 67;
                              break;
                           case 3:
                              var360 = 50;
                              break;
                           default:
                              var360 = 117;
                        }

                        var267[var334] = (char)(var347 ^ var360);
                        ++var2;
                        if (var66 == 0) {
                           var334 = var66;
                           var267 = var113;
                        } else {
                           if (var66 <= var2) {
                              var10000[5] = (new String(var113)).intern();
                              l = var10000;
                              return;
                           }

                           var267 = var113;
                           var334 = var2;
                        }
                     }
                  }

                  var227 = var35;
                  var334 = var2;
                  var347 = var35[var2];
                  switch (var2 % 5) {
                     case 0:
                        var360 = 76;
                        break;
                     case 1:
                        var360 = 94;
                        break;
                     case 2:
                        var360 = 67;
                        break;
                     case 3:
                        var360 = 50;
                        break;
                     default:
                        var360 = 117;
                  }
               }

               while(true) {
                  var227[var334] = (char)(var347 ^ var360);
                  ++var2;
                  if (var38 == 0) {
                     var334 = var38;
                     var227 = var113;
                     var347 = var113[var38];
                     switch (var2 % 5) {
                        case 0:
                           var360 = 76;
                           break;
                        case 1:
                           var360 = 94;
                           break;
                        case 2:
                           var360 = 67;
                           break;
                        case 3:
                           var360 = 50;
                           break;
                        default:
                           var360 = 117;
                     }
                  } else {
                     if (var38 <= var2) {
                        label107: {
                           var10000[3] = (new String(var113)).intern();
                           char[] var42 = "\u0006\b\u000e".toCharArray();
                           int var234 = var42.length;
                           var2 = 0;
                           var113 = var42;
                           int var45 = var234;
                           char[] var237;
                           if (var234 <= 1) {
                              var237 = var42;
                              var334 = var2;
                           } else {
                              var113 = var42;
                              var45 = var234;
                              if (var234 <= var2) {
                                 break label107;
                              }

                              var237 = var42;
                              var334 = var2;
                           }

                           while(true) {
                              var347 = var237[var334];
                              switch (var2 % 5) {
                                 case 0:
                                    var360 = 76;
                                    break;
                                 case 1:
                                    var360 = 94;
                                    break;
                                 case 2:
                                    var360 = 67;
                                    break;
                                 case 3:
                                    var360 = 50;
                                    break;
                                 default:
                                    var360 = 117;
                              }

                              var237[var334] = (char)(var347 ^ var360);
                              ++var2;
                              if (var45 == 0) {
                                 var334 = var45;
                                 var237 = var113;
                              } else {
                                 if (var45 <= var2) {
                                    break;
                                 }

                                 var237 = var113;
                                 var334 = var2;
                              }
                           }
                        }

                        var10000[4] = (new String(var113)).intern();
                        char[] var49 = "\u000f\u000e\u0016".toCharArray();
                        int var244 = var49.length;
                        var2 = 0;
                        var113 = var49;
                        int var52 = var244;
                        char[] var247;
                        if (var244 <= 1) {
                           var247 = var49;
                           var334 = var2;
                        } else {
                           var113 = var49;
                           var52 = var244;
                           if (var244 <= var2) {
                              var10000[5] = (new String(var49)).intern();
                              l = var10000;
                              return;
                           }

                           var247 = var49;
                           var334 = var2;
                        }

                        while(true) {
                           var347 = var247[var334];
                           switch (var2 % 5) {
                              case 0:
                                 var360 = 76;
                                 break;
                              case 1:
                                 var360 = 94;
                                 break;
                              case 2:
                                 var360 = 67;
                                 break;
                              case 3:
                                 var360 = 50;
                                 break;
                              default:
                                 var360 = 117;
                           }

                           var247[var334] = (char)(var347 ^ var360);
                           ++var2;
                           if (var52 == 0) {
                              var334 = var52;
                              var247 = var113;
                           } else {
                              if (var52 <= var2) {
                                 var10000[5] = (new String(var113)).intern();
                                 l = var10000;
                                 return;
                              }

                              var247 = var113;
                              var334 = var2;
                           }
                        }
                     }

                     var227 = var113;
                     var334 = var2;
                     var347 = var113[var2];
                     switch (var2 % 5) {
                        case 0:
                           var360 = 76;
                           break;
                        case 1:
                           var360 = 94;
                           break;
                        case 2:
                           var360 = 67;
                           break;
                        case 3:
                           var360 = 50;
                           break;
                        default:
                           var360 = 117;
                     }
                  }
               }
            }

            var207 = var113;
            var334 = var2;
            var347 = var113[var2];
            switch (var2 % 5) {
               case 0:
                  var360 = 76;
                  break;
               case 1:
                  var360 = 94;
                  break;
               case 2:
                  var360 = 67;
                  break;
               case 3:
                  var360 = 50;
                  break;
               default:
                  var360 = 117;
            }
         }
      }
   }
}
