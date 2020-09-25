package com.intellij.ui;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.RetinaImage;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SplashSlideLoader {
    private static final Path cacheHome = Paths.get(PathManager.getSystemPath(), "splashSlides");

    @Nullable
    public static Image loadImage(@NotNull String url) {
        var image = loadImageFromCache(url);
        if (image != null) return image;

        cacheAsync(url);
        return loadImageFromUrl(url);
    }

    public static void cacheAsync(@NotNull String url) {
        if (!isCacheNeeded(JBUIScale.sysScale())) return;

        // Don't use already loaded image to avoid oom
        NonUrgentExecutor.getInstance().execute(() -> {
            var cacheFile = getCacheFile(url, JBUIScale.sysScale());
            if (cacheFile == null) return;
            var image = loadImageFromUrl(url);
            if (image != null)  saveImage(cacheFile, FileUtilRt.getExtension(url), image);
        });
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void saveImage(@NotNull Path file, String extension, @NotNull Image image) {
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

    @Nullable
    private static Image loadImageFromCache(@NotNull String url) {
        float scale = JBUIScale.sysScale();
        if (isCacheNeeded(scale)) {
            var file = getCacheFile(url, scale);
            if (file != null) {
                try {
                    var fileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
                    if (!fileAttributes.isRegularFile()) {
                        return null;
                    }
                    Image image = ImageIO.read(file.toFile());
                    if (StartupUiUtil.isJreHiDPI()) {
                        image = RetinaImage.createFrom(image, scale, ImageLoader.ourComponent);
                    }
                    return image;
                } catch (IOException e) {
                    Logger.getInstance(SplashSlideLoader.class).error("Failed to load splash image", e);
                }
            }
        }
        return null;
    }

    private static boolean isCacheNeeded(float scale) {
        return scale != 1 && scale != 2;
    }

    @Nullable
    private static Image loadImageFromUrl(@NotNull String url) {
        return ImageLoader.loadFromUrl(
            url,
            SplashSlideLoader.class,
            ImageLoader.ALLOW_FLOAT_SCALING | ImageLoader.USE_IMAGE_IO,
            null,
            ScaleContext.create());
    }

    @Nullable
    private static Path getCacheFile(@NotNull String url, float scale) {
        byte[] bytes = FileUtilRt.getNameWithoutExtension(url).getBytes(StandardCharsets.UTF_8);
        String extension = FileUtilRt.getNameWithoutExtension(url);
        try {
            var d = MessageDigest.getInstance("SHA-256");
            //caches version
            d.update(bytes);
            var hex = StringUtil.toHexString(d.digest());
            return cacheHome.resolve(String.format("%s.x%s.%s", hex, scale, extension));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
