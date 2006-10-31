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
package org.intellij.images.vfs;

import com.intellij.javaee.web.WebModuleProperties;
import com.intellij.javaee.web.WebRoot;
import com.intellij.javaee.web.WebUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Image loader utility.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class IfsUtil {
    private static final Key<Long> TIMESTAMP_KEY = Key.create("Image.timeStamp");
    private static final Key<String> FORMAT_KEY = Key.create("Image.format");
    private static final Key<SoftReference<BufferedImage>> BUFFERED_IMAGE_REF_KEY = Key.create("Image.bufferedImage");

    /**
     * Load image data for file and put user data attributes into file.
     *
     * @param file File
     * @return true if file image is loaded.
     * @throws java.io.IOException if image can not be loaded
     */
    private static boolean refresh(@NotNull VirtualFile file) throws IOException {
        Long loadedTimeStamp = file.getUserData(TIMESTAMP_KEY);
        SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
        if (loadedTimeStamp == null || loadedTimeStamp < file.getTimeStamp() || imageRef == null || imageRef.get() == null) {
            try {
                final byte[] content = file.contentsToByteArray();
                InputStream inputStream = new ByteArrayInputStream(content, 0, content.length);
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);
                try {
                    Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
                    if (imageReaders.hasNext()) {
                        ImageReader imageReader = imageReaders.next();
                        try {
                            file.putUserData(FORMAT_KEY, imageReader.getFormatName());
                            ImageReadParam param = imageReader.getDefaultReadParam();
                            imageReader.setInput(imageInputStream, true, true);
                            int minIndex = imageReader.getMinIndex();
                            BufferedImage image = imageReader.read(minIndex, param);
                            file.putUserData(BUFFERED_IMAGE_REF_KEY, new SoftReference<BufferedImage>(image));
                            return true;
                        } finally {
                            imageReader.dispose();
                        }
                    }
                } finally {
                    imageInputStream.close();
                }
            } finally {
                // We perform loading no more needed
                file.putUserData(TIMESTAMP_KEY, System.currentTimeMillis());
            }
        }
        return false;
    }

    @Nullable
    public static BufferedImage getImage(@NotNull VirtualFile file) throws IOException {
        refresh(file);
        SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
        return imageRef != null ? imageRef.get() : null;
    }

    @Nullable
    public static String getFormat(@NotNull VirtualFile file) throws IOException {
        refresh(file);
        return file.getUserData(FORMAT_KEY);
    }

    public static String getReferencePath(Project project, VirtualFile file) {
        File ioFile = VfsUtil.virtualToIoFile(file);
        StringBuilder pathBuffer = new StringBuilder();
        WebModuleProperties wmp = WebUtil.getWebModuleProperties(project, file);
        if (wmp != null) {
            WebRoot root = WebUtil.findParentWebRoot(file, wmp.getWebRoots(true));
            if (root != null && root.getFile() != null) {
                File base = VfsUtil.virtualToIoFile(root.getFile());
                if (base.equals(ioFile)) {
                    pathBuffer.append(file.getPath());
                } else {
                    pathBuffer.append("/");
                    pathBuffer.append(FileUtil.getRelativePath(base, ioFile));
                }
            }
        }
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
        ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
        VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file);
        if (sourceRoot != null) {
            File base = VfsUtil.virtualToIoFile(sourceRoot);
            if (base.equals(ioFile)) {
                pathBuffer.append(file.getPath());
            } else {
                pathBuffer.append("/");
                pathBuffer.append(FileUtil.getRelativePath(base, ioFile));
            }
        }
        VirtualFile root = fileIndex.getContentRootForFile(file);
        if (root != null) {
            File base = VfsUtil.virtualToIoFile(root);
            if (base.equals(ioFile)) {
                pathBuffer.append(file.getPath());
            } else {
                pathBuffer.append("/");
                pathBuffer.append(FileUtil.getRelativePath(base, ioFile));
            }
        } else {
            pathBuffer.append(file.getPath());
        }
        return FileUtil.toSystemIndependentName(pathBuffer.toString());
    }
}
