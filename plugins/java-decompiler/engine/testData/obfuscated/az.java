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
      char[] var17;
      String[] var10000;
      label51: {
         var10000 = new String[2];
         char[] var10003 = "+\u0013*w'\u001b\u001e;j3^\u0018!kz\n\u001c(5w".toCharArray();
         int var10005 = var10003.length;
         int var1 = 0;
         var17 = var10003;
         int var5 = var10005;
         char[] var29;
         int var10006;
         if (var10005 <= 1) {
            var29 = var10003;
            var10006 = var1;
         } else {
            var17 = var10003;
            var5 = var10005;
            if (var10005 <= var1) {
               break label51;
            }

            var29 = var10003;
            var10006 = var1;
         }

         while(true) {
            char var10007 = var29[var10006];
            byte var10008;
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

            var29[var10006] = (char)(var10007 ^ var10008);
            ++var1;
            if (var5 == 0) {
               var10006 = var5;
               var29 = var17;
            } else {
               if (var5 <= var1) {
                  break;
               }

               var29 = var17;
               var10006 = var1;
            }
         }
      }

      var10000[0] = (new String(var17)).intern();
      char[] var9 = "^\u00187\u007f2\u001d\t*km^".toCharArray();
      int var36 = var9.length;
      int var2 = 0;
      var17 = var9;
      int var12 = var36;
      char[] var39;
      int var46;
      if (var36 <= 1) {
         var39 = var9;
         var46 = var2;
      } else {
         var17 = var9;
         var12 = var36;
         if (var36 <= var2) {
            var10000[1] = (new String(var9)).intern();
            e = var10000;
            return;
         }

         var39 = var9;
         var46 = var2;
      }

      while(true) {
         char var47 = var39[var46];
         byte var48;
         switch (var2 % 5) {
            case 0:
               var48 = 126;
               break;
            case 1:
               var48 = 125;
               break;
            case 2:
               var48 = 79;
               break;
            case 3:
               var48 = 15;
               break;
            default:
               var48 = 87;
         }

         var39[var46] = (char)(var47 ^ var48);
         ++var2;
         if (var12 == 0) {
            var46 = var12;
            var39 = var17;
         } else {
            if (var12 <= var2) {
               var10000[1] = (new String(var17)).intern();
               e = var10000;
               return;
            }

            var39 = var17;
            var46 = var2;
         }
      }
   }
}
