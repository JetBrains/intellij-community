/** $Id$ */
package org.intellij.images.ui;

import javax.swing.*;

/**
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public class ThumbnailComponent extends JComponent {
    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "ThumbnailComponentUI";

    static {
        UIManager.getDefaults().put(uiClassID, ThumbnailComponentUI.class.getName());
    }

    /**
     * Image component for rendering thumbnail image.
     */
    private final ImageComponent imageComponent = new ImageComponent();

    private String format;
    private long fileSize;
    private String fileName;
    private boolean directory;
    private int imagesCount;

    public ThumbnailComponent() {
        updateUI();
    }

    public ImageComponent getImageComponent() {
        return imageComponent;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        String oldValue = this.format;
        if (oldValue != null && !oldValue.equals(format) || oldValue == null && format != null) {
            this.format = format;
            firePropertyChange("format", oldValue, this.format);
        }
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        long oldValue = this.fileSize;
        if (oldValue != fileSize) {
            this.fileSize = fileSize;
            firePropertyChange("fileSize", oldValue, this.fileSize);
        }
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        String oldValue = this.fileName;
        if (oldValue != null && !oldValue.equals(fileName) || oldValue == null && fileName != null) {
            this.fileName = fileName;
            firePropertyChange("fileName", oldValue, this.fileName);
        }
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        boolean oldValue = this.directory;
        if (oldValue != directory) {
            this.directory = directory;
            firePropertyChange("directory", oldValue, this.directory);
        }
    }

    public int getImagesCount() {
        return imagesCount;
    }

    public void setImagesCount(int imagesCount) {
        int oldValue = this.imagesCount;
        if (oldValue != imagesCount) {
            this.imagesCount = imagesCount;
            firePropertyChange("imagesCount", oldValue, this.imagesCount);
        }
    }

    public String getFileSizeText() {
        if (fileSize < 0x400) {
            return fileSize + "b";
        }
        if (fileSize < 0x100000) {
            long kbytes = fileSize * 100 / 1024;
            return kbytes / 100 + "." + kbytes % 100 + "Kb";
        }
        long mbytes = fileSize * 100 / 1024;
        return mbytes / 100 + "." + mbytes % 100 + "Mb";
    }

    public void updateUI() {
        setUI(UIManager.getUI(this));
    }

    public String getUIClassID() {
        return uiClassID;
    }
}