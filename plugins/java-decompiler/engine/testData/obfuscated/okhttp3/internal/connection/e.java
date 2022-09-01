package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.Proxy.Type;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

public final class e extends okhttp3.internal.http2.d.c implements okhttp3.j {
   static final boolean h = true;
   private Protocol A;
   private okhttp3.internal.http2.d B;
   private okio.e C;
   private okio.d D;
   private int E;
   private int F = 1;
   public final okhttp3.internal.connection.f b;
   boolean c;
   int d;
   int e;
   final List<Reference<okhttp3.internal.connection.i>> f = new ArrayList();
   long g = 9223372036854775807L;
   private final okhttp3.ai w;
   private Socket x;
   private Socket y;
   private okhttp3.u z;

   public e(okhttp3.internal.connection.f var1, okhttp3.ai var2) {
      this.b = var1;
      this.w = var2;
   }

   private void G(int var1, int var2, int var3, okhttp3.f var4, okhttp3.r var5) throws IOException {
      okhttp3.ae var6 = this.N();
      HttpUrl var7 = var6.i();

      for(int var8 = 0; var8 < 21; ++var8) {
         this.H(var1, var2, var4, var5);
         var6 = this.M(var2, var3, var6, var7);
         if (var6 == null) {
            break;
         }

         okhttp3.internal.c.m(this.x);
         this.x = null;
         this.D = null;
         this.C = null;
         var5.g(var4, this.w.f(), this.w.e(), (Protocol)null);
         OkHttpClient.a.g(var4, this.w.f(), this.w.e(), (Protocol)null);
      }

   }

   private void H(int var1, int var2, okhttp3.f var3, okhttp3.r var4) throws IOException {
      Proxy var5 = this.w.e();
      okhttp3.a var6 = this.w.d();
      Socket var13;
      if (var5.type() != Type.DIRECT && var5.type() != Type.HTTP) {
         var13 = new Socket(var5);
      } else {
         var13 = var6.n().createSocket();
      }

      this.x = var13;
      var4.d(var3, this.w.f(), var5);
      OkHttpClient.a.d(var3, this.w.f(), var5);
      this.x.setSoTimeout(var2);

      StringBuilder var11;
      ConnectException var12;
      try {
         okhttp3.internal.e.e.n().a(this.x, this.w.f(), var1);
      } catch (ConnectException var9) {
         var11 = new StringBuilder();
         var11.append("Failed to connect to ");
         var11.append(this.w.f());
         var12 = new ConnectException(var11.toString());
         var12.initCause(var9);
         throw var12;
      } catch (NullPointerException var10) {
         var11 = new StringBuilder();
         var11.append("Failed to connect to ");
         var11.append(this.w.f());
         var12 = new ConnectException(var11.toString());
         var12.initCause(var10);
         throw var12;
      }

      try {
         this.C = okio.m.b(okio.m.k(this.x));
         this.D = okio.m.c(okio.m.e(this.x));
      } catch (NullPointerException var7) {
         throw new IOException(var7);
      } catch (IllegalArgumentException var8) {
         throw new IOException(var8);
      }
   }

   private void I(okhttp3.internal.connection.b var1, int var2, okhttp3.f var3, okhttp3.r var4) throws IOException {
      if (this.w.d().t() == null) {
         if (this.w.d().p().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
            this.y = this.x;
            this.A = Protocol.H2_PRIOR_KNOWLEDGE;
            this.J(var2);
         } else {
            this.y = this.x;
            this.A = Protocol.HTTP_1_1;
         }
      } else {
         var4.e(var3);
         OkHttpClient.a.e(var3);
         this.K(var1);
         var4.f(var3, this.z);
         OkHttpClient.a.f(var3, this.z);
         if (this.A == Protocol.HTTP_2) {
            this.J(var2);
         }

      }
   }

   private void J(int var1) throws IOException {
      this.y.setSoTimeout(0);
      okhttp3.internal.http2.d var2 = (new okhttp3.internal.http2.d.a(true)).i(this.y, this.w.d().l().j(), this.C, this.D).j(this).k(var1).l();
      this.B = var2;
      var2.K();
   }

   private void K(okhttp3.internal.connection.b param1) throws IOException {
      // $FF: Couldn't be decompiled
   }

   private boolean L(SSLSession var1) {
      boolean var2;
      if (!"NONE".equals(var1.getProtocol()) && !"SSL_NULL_WITH_NULL_NULL".equals(var1.getCipherSuite())) {
         var2 = true;
      } else {
         var2 = false;
      }

      return var2;
   }

   private okhttp3.ae M(int var1, int var2, okhttp3.ae var3, HttpUrl var4) throws IOException {
      StringBuilder var5 = new StringBuilder();
      var5.append("CONNECT ");
      var5.append(okhttp3.internal.c.u(var4, true));
      var5.append(" HTTP/1.1");
      String var10 = var5.toString();

      okhttp3.ag var11;
      do {
         okhttp3.internal.c.a var6 = new okhttp3.internal.c.a((OkHttpClient)null, (okhttp3.internal.connection.e)null, this.C, this.D);
         this.C.k().c((long)var1, TimeUnit.MILLISECONDS);
         this.D.k().c((long)var2, TimeUnit.MILLISECONDS);
         var6.p(var3.k(), var10);
         var6.e();
         var11 = var6.f(false).n(var3).C();
         var6.s(var11);
         int var7 = var11.p();
         if (var7 == 200) {
            if (this.C.f().j() && this.D.e().j()) {
               return null;
            }

            IOException var9 = new IOException("TLS tunnel buffered too many bytes!");
            throw var9;
         }

         if (var7 != 407) {
            StringBuilder var8 = new StringBuilder();
            var8.append("Unexpected response code for CONNECT: ");
            var8.append(var11.p());
            throw new IOException(var8.toString());
         }

         var3 = this.w.d().o().b(this.w, var11);
         if (var3 == null) {
            throw new IOException("Failed to authenticate with proxy");
         }
      } while(!"close".equalsIgnoreCase(var11.t("Connection")));

      return var3;
   }

   private okhttp3.ae N() throws IOException {
      okhttp3.ae var1 = (new okhttp3.ae.a()).i(this.w.d().l()).q("CONNECT", (okhttp3.af)null).k("Host", okhttp3.internal.c.u(this.w.d().l(), true)).k("Proxy-Connection", "Keep-Alive").k("User-Agent", okhttp3.internal.d.a()).v();
      okhttp3.ag var2 = (new okhttp3.ag.a()).n(var1).o(Protocol.HTTP_1_1).p(407).q("Preemptive Authenticate").v(okhttp3.internal.c.d).z(-1L).A(-1L).s("Proxy-Authenticate", "OkHttp-Preemptive").C();
      okhttp3.ae var3 = this.w.d().o().b(this.w, var2);
      if (var3 != null) {
         var1 = var3;
      }

      return var1;
   }

   private boolean O(List<okhttp3.ai> var1) {
      int var2 = var1.size();

      for(int var3 = 0; var3 < var2; ++var3) {
         okhttp3.ai var4 = (okhttp3.ai)var1.get(var3);
         if (var4.e().type() == Type.DIRECT && this.w.e().type() == Type.DIRECT && this.w.f().equals(var4.f())) {
            return true;
         }
      }

      return false;
   }

   public okhttp3.ai a() {
      return this.w;
   }

   public void i() {
      // $FF: Couldn't be decompiled
   }

   public void j(int param1, int param2, int param3, int param4, boolean param5, okhttp3.f param6, okhttp3.r param7) {
      // $FF: Couldn't be decompiled
   }

   boolean k(okhttp3.a var1, List<okhttp3.ai> var2) {
      if (this.f.size() < this.F && !this.c) {
         if (!okhttp3.internal.a.i.d(this.w.d(), var1)) {
            return false;
         }

         if (var1.l().j().equals(this.a().d().l().j())) {
            return true;
         }

         if (this.B == null) {
            return false;
         }

         if (var2 != null && this.O(var2)) {
            if (var1.u() != okhttp3.internal.g.d.a) {
               return false;
            }

            if (!this.l(var1.l())) {
               return false;
            }

            try {
               var1.v().d(var1.l().j(), this.s().e());
               return true;
            } catch (SSLPeerUnverifiedException var3) {
            }
         }
      }

      return false;
   }

   public boolean l(HttpUrl var1) {
      int var2 = var1.k();
      int var3 = this.w.d().l().k();
      boolean var4 = false;
      if (var2 != var3) {
         return false;
      } else if (!var1.j().equals(this.w.d().l().j())) {
         boolean var5 = var4;
         if (this.z != null) {
            var5 = var4;
            if (okhttp3.internal.g.d.a.b(var1.j(), (X509Certificate)this.z.e().get(0))) {
               var5 = true;
            }
         }

         return var5;
      } else {
         return true;
      }
   }

   okhttp3.internal.b.c m(OkHttpClient var1, okhttp3.z.a var2) throws SocketException {
      if (this.B != null) {
         return new okhttp3.internal.http2.e(var1, this, var2, this.B);
      } else {
         this.y.setSoTimeout(var2.f());
         this.C.k().c((long)var2.f(), TimeUnit.MILLISECONDS);
         this.D.k().c((long)var2.g(), TimeUnit.MILLISECONDS);
         return new okhttp3.internal.c.a(var1, this, this.C, this.D);
      }
   }

   public void n() {
      okhttp3.internal.c.m(this.x);
   }

   public Socket o() {
      return this.y;
   }

   public boolean p(boolean var1) {
      if (!this.y.isClosed() && !this.y.isInputShutdown() && !this.y.isOutputShutdown()) {
         okhttp3.internal.http2.d var2 = this.B;
         if (var2 != null) {
            return var2.M(System.nanoTime());
         } else if (var1) {
            boolean var10001;
            int var3;
            try {
               var3 = this.y.getSoTimeout();
            } catch (SocketTimeoutException var21) {
               var10001 = false;
               return true;
            } catch (IOException var22) {
               var10001 = false;
               return false;
            }

            boolean var13 = false;

            try {
               var13 = true;
               this.y.setSoTimeout(1);
               var1 = this.C.j();
               var13 = false;
            } finally {
               if (var13) {
                  try {
                     this.y.setSoTimeout(var3);
                  } catch (SocketTimeoutException var14) {
                     var10001 = false;
                     return true;
                  } catch (IOException var15) {
                     var10001 = false;
                     return false;
                  }
               }
            }

            if (var1) {
               try {
                  this.y.setSoTimeout(var3);
                  return false;
               } catch (SocketTimeoutException var16) {
                  var10001 = false;
                  return true;
               } catch (IOException var17) {
                  var10001 = false;
               }
            } else {
               try {
                  this.y.setSoTimeout(var3);
                  return true;
               } catch (SocketTimeoutException var18) {
                  var10001 = false;
                  return true;
               } catch (IOException var19) {
                  var10001 = false;
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public void q(okhttp3.internal.http2.g var1) throws IOException {
      var1.r(ErrorCode.REFUSED_STREAM, (IOException)null);
   }

   public void r(okhttp3.internal.http2.d param1) {
      // $FF: Couldn't be decompiled
   }

   public okhttp3.u s() {
      return this.z;
   }

   public boolean t() {
      boolean var1;
      if (this.B != null) {
         var1 = true;
      } else {
         var1 = false;
      }

      return var1;
   }

   public String toString() {
      StringBuilder var1 = new StringBuilder();
      var1.append("Connection{");
      var1.append(this.w.d().l().j());
      var1.append(":");
      var1.append(this.w.d().l().k());
      var1.append(", proxy=");
      var1.append(this.w.e());
      var1.append(" hostAddress=");
      var1.append(this.w.f());
      var1.append(" cipherSuite=");
      okhttp3.u var2 = this.z;
      Object var3;
      if (var2 != null) {
         var3 = var2.d();
      } else {
         var3 = "none";
      }

      var1.append(var3);
      var1.append(" protocol=");
      var1.append(this.A);
      var1.append('}');
      return var1.toString();
   }

   void u(IOException var1) {
      if (!h && Thread.holdsLock(this.b)) {
         throw new AssertionError();
      } else {
         okhttp3.internal.connection.f var2 = this.b;
         synchronized(var2){}

         Throwable var10000;
         boolean var10001;
         label870: {
            label876: {
               ErrorCode var94;
               label877: {
                  label867: {
                     int var3;
                     try {
                        if (!(var1 instanceof StreamResetException)) {
                           break label867;
                        }

                        var94 = ((StreamResetException)var1).errorCode;
                        if (var94 != ErrorCode.REFUSED_STREAM) {
                           break label877;
                        }

                        var3 = this.E + 1;
                        this.E = var3;
                     } catch (Throwable var93) {
                        var10000 = var93;
                        var10001 = false;
                        break label870;
                     }

                     if (var3 > 1) {
                        try {
                           this.c = true;
                           ++this.d;
                        } catch (Throwable var89) {
                           var10000 = var89;
                           var10001 = false;
                           break label870;
                        }
                     }
                     break label876;
                  }

                  try {
                     if (this.t() && !(var1 instanceof ConnectionShutdownException)) {
                        break label876;
                     }
                  } catch (Throwable var92) {
                     var10000 = var92;
                     var10001 = false;
                     break label870;
                  }

                  try {
                     this.c = true;
                     if (this.e != 0) {
                        break label876;
                     }
                  } catch (Throwable var91) {
                     var10000 = var91;
                     var10001 = false;
                     break label870;
                  }

                  if (var1 != null) {
                     try {
                        this.b.k(this.w, var1);
                     } catch (Throwable var88) {
                        var10000 = var88;
                        var10001 = false;
                        break label870;
                     }
                  }

                  try {
                     ++this.d;
                     break label876;
                  } catch (Throwable var87) {
                     var10000 = var87;
                     var10001 = false;
                     break label870;
                  }
               }

               try {
                  if (var94 != ErrorCode.CANCEL) {
                     this.c = true;
                     ++this.d;
                  }
               } catch (Throwable var90) {
                  var10000 = var90;
                  var10001 = false;
                  break label870;
               }
            }

            label836:
            try {
               return;
            } catch (Throwable var86) {
               var10000 = var86;
               var10001 = false;
               break label836;
            }
         }

         while(true) {
            Throwable var95 = var10000;

            try {
               throw var95;
            } catch (Throwable var85) {
               var10000 = var85;
               var10001 = false;
               continue;
            }
         }
      }
   }
}
