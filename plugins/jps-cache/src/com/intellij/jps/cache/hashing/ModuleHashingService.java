package com.intellij.jps.cache.hashing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsFileTypesConfigurationImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.hashing.ModuleHashingService");
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final byte CARRIAGE_RETURN_CODE = 13;
  private static final byte LINE_FEED_CODE = 10;
  protected static final int HASH_SIZE_IN_BYTES = 16;
  private static final String DIGEST_ALGORITHM = "MD5";
  private final IgnoredPatternSet myIgnoredPatternSet;

  public ModuleHashingService() {
    JpsFileTypesConfigurationImpl configuration = new JpsFileTypesConfigurationImpl();
    myIgnoredPatternSet = new IgnoredPatternSet();
    myIgnoredPatternSet.setIgnoreMasks(configuration.getIgnoredPatternString());
  }

  public Optional<byte[]> hashDirectories(File[] directories) {
    byte[] hash = null;
    for (File curContentRoot : directories) {
      byte[] curHash = hashDirectory(curContentRoot, new RelativeToDirectoryRelativizer(curContentRoot.getPath()));
      if (curHash == null) continue;
      hash = sum(hash, curHash);
    }
    return hash == null ? Optional.empty() : Optional.of(hash);
  }

  private byte[] hashDirectory(File dir, RelativeToDirectoryRelativizer relativizer) {
    List<File> filesList = Arrays.stream(Optional.ofNullable(dir.listFiles()).orElse(new File[0]))
      .filter(file -> !myIgnoredPatternSet.isIgnored(file.getName())).collect(Collectors.toList());

    if (filesList.size() == 1 && filesList.get(0).getName().endsWith(".iml")) return null;
    byte[] hash = null;
    for (File file : filesList) {
      byte[] curHash = file.isDirectory() ? hashDirectory(file, relativizer) : hashFile(file, relativizer);
      if (curHash == null) continue;
      hash = sum(hash, curHash);
    }
    return hash;
  }

  private static byte[] hashFile(File file, PathRelativizer relativizer) {
    try (FileInputStream fis = new FileInputStream(file)) {
      MessageDigest md = getMessageDigest();
      md.reset();
      //noinspection ImplicitDefaultCharsetUsage,SSBasedInspection
      md.update(relativizer.relativize(file).getBytes());
      byte[] buf = new byte[1024 * 1024];
      int length;
      while ((length = fis.read(buf)) != -1) {
        byte[] res = new byte[length];
        int copiedBytes = 0;
        for (int i = 0; i < length; i++) {
          if (buf[i] != CARRIAGE_RETURN_CODE && ((i + 1) >= length || buf[i + 1] != LINE_FEED_CODE)) {
            res[copiedBytes] = buf[i];
            copiedBytes++;
          }
        }
        md.update(copiedBytes != res.length ? Arrays.copyOf(res, res.length - (res.length - copiedBytes)) : res);
      }
      return md.digest();
    }
    catch (IOException e) {
      LOG.warn(String.format("Error while hashing file %s : ", file.getAbsolutePath()), e);
      return null;
    }
  }

  @NotNull
  private static byte[] sum(byte[] firstHash, byte[] secondHash) {
    byte[] result = firstHash != null ? firstHash : new byte[HASH_SIZE_IN_BYTES];
    for (int i = 0; i < result.length; i++) {
      result[i] += secondHash[i];
    }
    return result;
  }

  public static String calculateStringHash(String content) {
    try {
      MessageDigest md = getMessageDigest();
      md.reset();
      return StringUtil.toHexString(md.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
    catch (IOException e) {
      LOG.warn("Couldn't calculate string hash for: " + content);
    }
    return "";
  }

  @NotNull
  private static MessageDigest getMessageDigest() throws IOException {
    MessageDigest messageDigest = MESSAGE_DIGEST_THREAD_LOCAL.get();
    if (messageDigest != null) return messageDigest;
    try {
      messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
      MESSAGE_DIGEST_THREAD_LOCAL.set(messageDigest);
      return messageDigest;
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }
}

