package com.intellij.ui;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.RetinaImage;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SplashSlideLoader {
    private static final Path cacheHome = Paths.get(PathManager.getSystemPath(), "splashSlides");

    public static Image loadImage(String url) {
        var image = loadImageFromCache(url);
        if (image != null) return image;

        cacheAsync(url);
        return loadSlow(url);
    }

    public static void cacheAsync(String url) {
        var scale = getScale();
        if (scale == 1 || scale == 2) return;

        // Don't use already loaded image to avoid oom
        NonUrgentExecutor.getInstance().execute(() -> cache(url));
    }

    private static void cache(String url) {
        try (var info = GetStream(url)) {
            if (info.stream == null) return;

            var bytes = FileUtilRt.loadBytes(info.stream);
            var cacheFile = getCacheFile(bytes, getScale(), info.extension);

            var image = loadSlow(url);
            saveImage(cacheFile, info.extension, image);
        } catch (Exception ignored) {
        }
    }

    public static Image loadImageFromCache(String url) {
        try (var info = GetStream(url)) {
            var scale = getScale();
            if (info.stream == null) return null;

            if (scale == 1 || scale == 2) {
                var image = ImageIO.read(info.stream);
                return info.withRetina ? withRetina(image, scale) : image;
            }

            var bytes = FileUtilRt.loadBytes(info.stream);
            var cacheFile = getCacheFile(bytes, scale, info.extension);
            if (cacheFile != null) return loadFromCache(cacheFile, scale);

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get2xImageUrl(String url) {
        return FileUtilRt.getNameWithoutExtension(url) + "@2x." + FileUtilRt.getExtension(url);
    }

    private static float getScale() {
        return JreHiDpiUtil.isJreHiDPIEnabled() ? JBUIScale.sysScale() : 1;
    }

    private static void saveImage(Path file, String extension, Image image) {
        try {
            var tmp = file.resolve(file.toString() + ".tmp" + System.currentTimeMillis());
            var tmpFile = tmp.toFile();

            tmpFile.getParentFile().mkdir();
            if (!tmpFile.createNewFile()) return;

            try {
                ImageIO.write(ImageUtil.toBufferedImage(image), extension, tmpFile);

                try {
                    Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file);
                }
            } finally {
                tmpFile.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Info GetStream(String url) {
        String extension = FileUtilRt.getExtension(url);
        if (getScale() != 1) {
            var url2 = get2xImageUrl(url);
            var stream = SplashSlideLoader.class.getResourceAsStream(url2);
            if (stream != null) {
                return new Info(stream, true, extension);
            }
        }

        return new Info(SplashSlideLoader.class.getResourceAsStream(url), false, extension);
    }

    private static Image loadSlow(String url) {
        return ImageLoader.loadFromUrl(
                url,
                SplashSlideLoader.class,
                ImageLoader.ALLOW_FLOAT_SCALING,
                null,
                ScaleContext.create());
    }

    private static Image loadFromCache(Path file, float scale) {
        try {
            var fileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!fileAttributes.isRegularFile()) {
                return null;
            }
            return withRetina(ImageIO.read(file.toFile()), scale);
        } catch (IOException ignore) {
            return null;
        }
    }

    private static Path getCacheFile(byte[] imageBytes, float scale, String extension) {
        try {
            var d = MessageDigest.getInstance("SHA-256");
            //caches version
            d.update(imageBytes);
            var hex = StringUtil.toHexString(d.digest());
            return cacheHome.resolve(String.format("%s.x%s.%s", hex, scale, extension));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
    private static Image withRetina(Image image, float scale) {
        return RetinaImage.createFrom(image, scale, ImageLoader.ourComponent);
    }

    private static class Info implements AutoCloseable {
        public final InputStream stream;
        private final boolean withRetina;
        public final String extension;

        private Info(InputStream stream, boolean withRetina, String extension) {
            this.stream = stream;
            this.withRetina = withRetina;
            this.extension = extension;
        }

        @Override
        public void close() throws Exception {
            try {
                if (stream != null) stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
