package org.intellij.images.fileTypes.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.fileTypes.UserFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;

import javax.imageio.ImageIO;
import java.util.HashSet;
import java.util.Set;

/**
 * Image file type manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileTypeManagerImpl extends ImageFileTypeManager implements ApplicationComponent {
    private static final String NAME = "ImagesFileTypeManager";
    
    private static final String[] EMPTY_STRING_ARRAY = new String[] {};
    private static final String IMAGE_FILE_TYPE_NAME = "Images";
    private static final String IMAGE_FILE_TYPE_DESCRIPTION = "Images file";
    private UserFileType imageFileType;

    public boolean isImage(VirtualFile file) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        FileType fileTypeByFile = fileTypeManager.getFileTypeByFile(file);
        return fileTypeByFile instanceof ImageFileType;
    }

    public FileType getImageFileType() {
        return imageFileType;
    }

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        String[] readerFormatNames = ImageIO.getReaderFormatNames();
        Set<String> extensions = new HashSet<String>(readerFormatNames.length);
        for (String format : readerFormatNames) {
            extensions.add(format.toLowerCase());
        }
        imageFileType = new ImageFileType();
        imageFileType.setIcon(IconLoader.getIcon("/org/intellij/images/fileTypes/icons/ImagesFileType.png"));
        imageFileType.setName(IMAGE_FILE_TYPE_NAME);
        imageFileType.setDescription(IMAGE_FILE_TYPE_DESCRIPTION);
        fileTypeManager.registerFileType(imageFileType, extensions.toArray(EMPTY_STRING_ARRAY));
    }

    public void disposeComponent() {
    }

    public static final class ImageFileType extends UserBinaryFileType {
    }
}
