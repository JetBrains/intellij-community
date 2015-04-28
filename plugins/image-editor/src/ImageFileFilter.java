import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * Created by Tom on 28/04/2015.
 */
public class ImageFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        final String name = f.getName();
        return name.endsWith(".png") || name.endsWith("jpg");
    }

    @Override
    public String getDescription() {
        return "*.png,*.jpg";
    }
}
