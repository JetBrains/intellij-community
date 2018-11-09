import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class bc extends DefaultHandler {
   private boolean a = false;
   private StringBuilder b = new StringBuilder();
   private Map<String, ay> c = new TreeMap();
   private List<az> d = new ArrayList();
   public static int e;

   public void characters(char[] var1, int var2, int var3) throws SAXException {
      this.a = true;
      this.b.append(var1, var2, var3);
   }

   public void endDocument() throws SAXException {
   }

   public void endElement(String var1, String var2, String var3) throws SAXException {
      int var7 = e;

      bc var10000;
      label63: {
         label67: {
            try {
               var10000 = this;
               if (var7 != 0) {
                  break label63;
               }

               if (!this.a) {
                  break label67;
               }
            } catch (SAXException var10) {
               throw var10;
            }

            String var4 = this.b.toString();
            Iterator var5 = this.d.iterator();

            label55: {
               while(var5.hasNext()) {
                  az var6 = (az)var5.next();

                  try {
                     var6.a(var4);
                     if (var7 != 0) {
                        break label55;
                     }

                     if (var7 != 0) {
                        break;
                     }
                  } catch (SAXException var9) {
                     throw var9;
                  }
               }

               this.b = new StringBuilder();
            }

            this.a = false;
         }

         var10000 = this;
      }

      Iterator var11 = var10000.d.iterator();

      while(var11.hasNext()) {
         az var12 = (az)var11.next();

         try {
            if (var12.b(var1, var3)) {
               var11.remove();
            }
         } catch (SAXException var8) {
            throw var8;
         }

         if (var7 != 0) {
            break;
         }
      }

   }

   public void processingInstruction(String var1, String var2) throws SAXException {
      Iterator var3 = this.d.iterator();

      while(var3.hasNext()) {
         az var4 = (az)var3.next();
         var4.a(var1, var2);
      }

   }

   public void startElement(String var1, String var2, String var3, Attributes var4) throws SAXException {
      Iterator var5 = this.d.iterator();

      while(var5.hasNext()) {
         az var6 = (az)var5.next();
         var6.a(var1, var3, var4);
      }

      try {
         ay var9 = (ay)this.c.get(var3);

         try {
            if (var9 != null) {
               this.d.add(new az(var9, var1, var3, var4));
            }

         } catch (ParserConfigurationException var7) {
            throw var7;
         }
      } catch (ParserConfigurationException var8) {
         throw new SAXException(var8);
      }
   }

   public void a(String var1, ay var2) {
      this.c.put(var1, var2);
   }

   public static a0 a(Node var0) {
      return new a1(var0);
   }

   public void a(InputStream var1) throws ParserConfigurationException, SAXException, IOException {
      try {
         SAXParserFactory var2 = SAXParserFactory.newInstance();
         SAXParser var3 = var2.newSAXParser();
         XMLReader var4 = var3.getXMLReader();
         var4.setEntityResolver(new a2(this));
         var4.setContentHandler(this);
         var4.parse(new InputSource(var1));
      } catch (a_ var8) {
      } finally {
         var1.close();
      }

   }
}
