import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.Attributes;

class az {
   private Document a;
   private Node b;
   private Node c;
   private ay d;
   private static final String[] e;

   public az(ay var1, String var2, String var3, Attributes var4) throws ParserConfigurationException {
      this.d = var1;
      DocumentBuilderFactory var5 = DocumentBuilderFactory.newInstance();
      DocumentBuilder var6 = var5.newDocumentBuilder();
      this.a = var6.newDocument();
      this.a(var3, var4);
   }

   private boolean a() {
      try {
         if (this.b()) {
            this.d.a(new a1(this.b));
            return true;
         }
      } catch (DOMException var1) {
         throw var1;
      }

      this.c = this.c.getParentNode();
      return false;
   }

   private boolean b() {
      return this.c.equals(this.b);
   }

   private void a(String param1, Attributes param2) {
      // $FF: Couldn't be decompiled
   }

   public Node c() {
      return this.b;
   }

   public void a(String var1, String var2, Attributes var3) {
      this.a(var2, var3);
   }

   public void a(String var1, String var2) {
      ProcessingInstruction var3 = this.a.createProcessingInstruction(var1, var2);
      this.c.appendChild(var3);
   }

   public boolean b(String var1, String var2) {
      try {
         if (!this.c.getNodeName().equals(var2)) {
            throw new DOMException((short)12, e[0] + var2 + e[1] + this.c.getNodeName());
         }
      } catch (DOMException var3) {
         throw var3;
      }

      return this.a();
   }

   public void a(String var1) {
      this.c.appendChild(this.a.createTextNode(var1));
   }

   public ay d() {
      return this.d;
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
      label51: {
         var10000 = new String[2];
         var10003 = "+\u0013*w'\u001b\u001e;j3^\u0018!kz\n\u001c(5w".toCharArray();
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
               break label51;
            }

            var4 = var10003;
            var10006 = var1;
         }

         while(true) {
            var10007 = var4[var10006];
            switch (var1 % 5) {
               case 0:
                  var10008 = 126;
                  break;
               case 1:
                  var10008 = 125;
                  break;
               case 2:
                  var10008 = 79;
                  break;
               case 3:
                  var10008 = 15;
                  break;
               default:
                  var10008 = 87;
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
      var10003 = "^\u00187\u007f2\u001d\t*km^".toCharArray();
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
            var10000[1] = (new String(var10003)).intern();
            e = var10000;
            return;
         }

         var4 = var10003;
         var10006 = var1;
      }

      while(true) {
         var10007 = var4[var10006];
         switch (var1 % 5) {
            case 0:
               var10008 = 126;
               break;
            case 1:
               var10008 = 125;
               break;
            case 2:
               var10008 = 79;
               break;
            case 3:
               var10008 = 15;
               break;
            default:
               var10008 = 87;
         }

         var4[var10006] = (char)(var10007 ^ var10008);
         ++var1;
         if (var2 == 0) {
            var10006 = var2;
            var4 = var10004;
         } else {
            if (var2 <= var1) {
               var10000[1] = (new String(var10004)).intern();
               e = var10000;
               return;
            }

            var4 = var10004;
            var10006 = var1;
         }
      }
   }
}
