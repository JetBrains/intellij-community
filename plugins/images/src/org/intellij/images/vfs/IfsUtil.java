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

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
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
     */
    private static boolean refresh(@NotNull VirtualFile file) throws IOException {
        Long loadedTimeStamp = file.getUserData(TIMESTAMP_KEY);
        SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
        if (loadedTimeStamp == null || loadedTimeStamp < file.getTimeStamp() || imageRef == null || imageRef.get() == null) {
            try {
                InputStream inputStream = file.getInputStream();
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

    public static @Nullable BufferedImage getImage(@NotNull VirtualFile file) throws IOException {
        refresh(file);
        SoftReference<BufferedImage> imageRef = file.getUserData(BUFFERED_IMAGE_REF_KEY);
        return imageRef != null ? imageRef.get() : null;
    }

    public static @Nullable String getFormat(@NotNull VirtualFile file) throws IOException {
        refresh(file);
        return file.getUserData(FORMAT_KEY);
    }

}
