package com.intellij.jps.cache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

interface PathRelativizer {
  String relativize(File target);
}

class ModuleHashingService {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.cachepuller.ModuleHashingService");
  private static final int HASH_SIZE_IN_BYTES = 16;

  byte[] hashContentRoots(File[] contentRoots) {
    byte[] hash = new byte[HASH_SIZE_IN_BYTES];

    for (File curContentRoot : contentRoots) {
      byte[] curHash = hashDirectory(curContentRoot, new RelativeToDirectoryRelativizer(curContentRoot.getPath()));
      sum(hash, curHash);
    }

    return hash;
  }

  byte[] hashDirectory(File dir, RelativeToDirectoryRelativizer relativizer) {
    File[] fileList = Optional.ofNullable(dir.listFiles()).orElse(new File[0]);
    byte[] hash = new byte[HASH_SIZE_IN_BYTES];


    for (File file : fileList) {
      byte[] curHash = file.isDirectory() ? hashDirectory(file, relativizer) : hashFile(file, relativizer);
      sum(hash, curHash);
    }

    byte[] dirNameHash = hashFileName(dir);
    sum(hash, dirNameHash);

    return hash;
  }

  byte[] hashFile(File file, PathRelativizer relativizer) {
    String filePathRelativeToModule = relativizer.relativize(file);
    byte[] fileNameBytes = filePathRelativeToModule.getBytes();
    byte[] buffer = new byte[(int)file.length() + fileNameBytes.length];
    try (FileInputStream fileInputStream = new FileInputStream(file)) {
      fileInputStream.read(buffer);
    }
    catch (FileNotFoundException e) {
      LOG.warn("File not found: ", e);
      return null;
    }
    catch (IOException e) {
      LOG.warn(String.format("Error while hashing file %s : ", file.getAbsolutePath()), e);
      return null;
    }
    for (int i = (int)file.length(), j = 0; i < buffer.length; ++i, ++j) {
      buffer[i] = fileNameBytes[j];
    }

    MessageDigest messageDigest = null;
    try {
      messageDigest = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.warn("MD5 hashing algorithm not found: ", e);
    }
    messageDigest.reset();
    messageDigest.update(buffer);
    return messageDigest.digest();
  }

  private byte[] hashFileName(File file) {
    byte[] fileNameBytes = file.getName().getBytes();
    MessageDigest messageDigest = null;
    try {
      messageDigest = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.warn("MD5 hashing algorithm not found : ", e);
      return null;
    }
    messageDigest.reset();
    messageDigest.update(fileNameBytes);
    return messageDigest.digest();
  }

  private void sum(byte[] firstHash, byte[] secondHash) {
    for (int i = 0; i < firstHash.length; ++i) {
      firstHash[i] += secondHash[i];
    }
  }
}

class PersistentCachingModuleHashingService extends ModuleHashingService {
  private final PersistentHashMap<String, byte[]> hashCache;

  PersistentCachingModuleHashingService(File cacheFile) throws IOException {
    this.hashCache =
      new PersistentHashMap<>(cacheFile, EnumeratorStringDescriptor.INSTANCE, ByteArrayDescriptor.INSTANCE);
  }

  byte[] hashContentRootsAndPersist(Module module, File[] contentRoots) throws IOException {
    byte[] directoriesHash = super.hashContentRoots(contentRoots);
    cache(module.getName(), directoriesHash);
    return directoriesHash;
  }

  byte[] getFromCache(String moduleName) throws IOException {
    return hashCache.get(moduleName);
  }

  private void cache(String moduleName, byte[] hash) throws IOException {
    hashCache.put(moduleName, hash);
  }

  private void cacheIfAbsent(String moduleName, byte[] hash) throws IOException {
    byte[] hashFromCache = hashCache.get(moduleName);
    if (hashFromCache != null) {
      return;
    }
    hashCache.put(moduleName, hash);
  }
}

class RelativeToDirectoryRelativizer implements PathRelativizer {
  private final String rootPath;

  RelativeToDirectoryRelativizer(String rootModulePath) {
    this.rootPath = rootModulePath;
  }

  @Override
  public String relativize(File target) {
    return Paths.get(rootPath).relativize(Paths.get(target.getPath())).toString();
  }
}

