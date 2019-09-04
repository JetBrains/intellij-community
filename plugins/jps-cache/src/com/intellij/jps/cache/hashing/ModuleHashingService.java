package com.intellij.jps.cache.hashing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.impl.JpsFileTypesConfigurationImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.hashing.ModuleHashingService");
  private static final byte CARRIAGE_RETURN_CODE = 13;
  protected static final int HASH_SIZE_IN_BYTES = 16;
  private static final String DIGEST_ALGORITHM = "MD5";
  private final IgnoredPatternSet myIgnoredPatternSet;
  private final MessageDigest myMessageDigest;

  public ModuleHashingService() throws IOException {
    myMessageDigest = initializeMessageDigest();
    JpsFileTypesConfigurationImpl configuration = new JpsFileTypesConfigurationImpl();
    myIgnoredPatternSet = new IgnoredPatternSet();
    myIgnoredPatternSet.setIgnoreMasks(configuration.getIgnoredPatternString());
  }

  private static MessageDigest initializeMessageDigest() throws IOException {
    try {
      return MessageDigest.getInstance(DIGEST_ALGORITHM);
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  public Optional<byte[]> hashDirectories(File[] directories) {
    byte[] hash = new byte[HASH_SIZE_IN_BYTES];

    for (File curContentRoot : directories) {
      byte[] curHash = hashDirectory(curContentRoot, new RelativeToDirectoryRelativizer(curContentRoot.getPath()));
      sum(hash, curHash);
    }

    return Arrays.equals(hash, new byte[HASH_SIZE_IN_BYTES]) ? Optional.empty() : Optional.of(hash);
  }

  private byte[] hashDirectory(File dir, RelativeToDirectoryRelativizer relativizer) {
    List<File> filesList = Arrays.stream(Optional.ofNullable(dir.listFiles()).orElse(new File[0]))
      .filter(file -> !myIgnoredPatternSet.isIgnored(file.getName())).collect(Collectors.toList());

    byte[] hash = new byte[HASH_SIZE_IN_BYTES];
    if (filesList.size() == 1 && filesList.get(0).getName().endsWith(".iml")) return hash;
    for (File file : filesList) {
      byte[] curHash = file.isDirectory() ? hashDirectory(file, relativizer) : hashFile(file, relativizer);
      if (curHash == null) continue;
      sum(hash, curHash);
    }

    return hash;
  }

  private byte[] hashFile(File file, PathRelativizer relativizer) {
    try {
      byte[] fileNameBytes = relativizer.relativize(file).getBytes();
      byte[] bytes = readAllBytesWithoutCarriageReturnChar(file, fileNameBytes);
      return myMessageDigest.digest(bytes);
    }
    catch (IOException e) {
      LOG.warn(String.format("Error while hashing file %s : ", file.getAbsolutePath()), e);
      return null;
    }
  }

  @NotNull
  private static byte[] readAllBytesWithoutCarriageReturnChar(@NotNull File file, @NotNull byte[] fileNameBytes) throws IOException {
    byte[] fileBytes = Files.readAllBytes(file.toPath());
    byte[] result = new byte[fileBytes.length + fileNameBytes.length];
    int copiedBytes = 0;
    for (byte fileNameByte : fileNameBytes) {
      result[copiedBytes] = fileNameByte;
      copiedBytes++;
    }
    for (byte fileByte : fileBytes) {
      if (fileByte != CARRIAGE_RETURN_CODE) {
        result[copiedBytes] = fileByte;
        copiedBytes++;
      }
    }
    return copiedBytes != result.length ? Arrays.copyOf(result, result.length - (result.length - copiedBytes)) : result;
  }

  private static void sum(byte[] firstHash, byte[] secondHash) {
    for (int i = 0; i < firstHash.length; ++i) {
      firstHash[i] += secondHash[i];
    }
  }
}

