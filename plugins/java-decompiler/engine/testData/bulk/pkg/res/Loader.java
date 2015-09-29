package pkg.res;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;

public class Loader {
   public String getResource() {
      URL resource = this.getClass().getClassLoader().getResource("pkg/res/resource.txt");
      if(resource == null) {
         throw new RuntimeException("Resource missing");
      } else {
         try {
            File e = new File(resource.toURI());
            byte[] bytes = new byte[(int)e.length()];
            FileInputStream stream = new FileInputStream(e);
            stream.read(bytes);
            stream.close();
            return new String(bytes, "UTF-8");
         } catch (Exception var5) {
            throw new RuntimeException("Resource load failed", var5);
         }
      }
   }
}
