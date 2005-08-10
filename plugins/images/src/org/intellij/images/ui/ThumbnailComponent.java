/** $Id$ */
/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.ui;

import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;

/**
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
@SuppressWarnings({"HardCodedStringLiteral"})
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
            firePropertyChange("fileSize", new Long(oldValue), new Long(this.fileSize));
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
        return StringUtil.formatFileSize(fileSize);
    }

    public void updateUI() {
        setUI(UIManager.getUI(this));
    }

    public String getUIClassID() {
        return uiClassID;
    }
}