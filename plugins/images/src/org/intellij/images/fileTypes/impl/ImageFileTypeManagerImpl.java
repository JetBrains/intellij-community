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
package org.intellij.images.fileTypes.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UserBinaryFileType;
import com.intellij.openapi.fileTypes.UserFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.util.HashSet;
import java.util.Set;

/**
 * Image file type manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileTypeManagerImpl extends ImageFileTypeManager implements ApplicationComponent {
    @NonNls private static final String NAME = "ImagesFileTypeManager";
    
    private static final String[] EMPTY_STRING_ARRAY = new String[] {};
    @NonNls private static final String IMAGE_FILE_TYPE_NAME = "Images";
    private static final String IMAGE_FILE_TYPE_DESCRIPTION = ImagesBundle.message("images.filetype.description");
    private UserFileType imageFileType;

    public boolean isImage(VirtualFile file) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        FileType fileTypeByFile = fileTypeManager.getFileTypeByFile(file);
        return fileTypeByFile instanceof ImageFileType;
    }

    public FileType getImageFileType() {
        return imageFileType;
    }

    @NotNull
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
        imageFileType.setIcon(IconLoader.getIcon("/org/intellij/images/icons/ImagesFileType.png"));
        imageFileType.setName(IMAGE_FILE_TYPE_NAME);
        imageFileType.setDescription(IMAGE_FILE_TYPE_DESCRIPTION);
        fileTypeManager.registerFileType(imageFileType, extensions.toArray(EMPTY_STRING_ARRAY));
    }

    public void disposeComponent() {
    }

    public static final class ImageFileType extends UserBinaryFileType {
    }
}
