// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Base64;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.util.ManifestDigester;
import sun.security.util.SignatureFileVerifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SecureJarLoader extends JarLoader {
  private static final Map<String, MessageDigest> ourPristineDigests = ContainerUtil.newHashMap();
  private static final Object ourPristineDigestsMonitor = new Object();

  private final MultiMap<String, Map.Entry<String, byte[]>> myDigestByFileName;

  /**
   * Map from file name inside ZIP archive to collection of code signers.
   * Implementation via Hashtable is required by {@link SignatureFileVerifier}.
   */
  private Hashtable<String, CodeSigner[]> myCodeSignersMap;
  private Set<String> myVerifiedEntries;

  SecureJarLoader(URL url, int index, ClassPath configuration) throws IOException {
    super(url, index, configuration);
    myCodeSignersMap = new Hashtable<String, CodeSigner[]>();
    myDigestByFileName = MultiMap.createSmart();
    myVerifiedEntries = ContainerUtil.newHashSet();
  }

  @Nullable
  @Override
  protected Attributes loadManifestAttributes(@NotNull ZipFile zipFile, @Nullable InputStream stream) {
    if (stream == null) return null;
    Manifest manifest;
    byte[] manifestBytes;
    try {
      manifestBytes = consumeAndReadFully(stream);
      verifyManifestAndLoadDigests(zipFile, manifestBytes);
      manifest = new Manifest(new ByteArrayInputStream(manifestBytes));
    }
    catch (IOException e) {
      return null;
    }
    catch (CertificateException e) {
      throw new SecurityException(e);
    }
    catch (NoSuchAlgorithmException e) {
      throw new SecurityException(e);
    }
    catch (SignatureException e) {
      throw new SecurityException(e);
    }

    loadDigests(manifest);

    return manifest.getMainAttributes();
  }

  private static byte[] consumeAndReadFully(@NotNull InputStream stream) throws IOException {
    try {
      return FileUtilRt.loadBytes(stream);
    }
    finally {
      stream.close();
    }
  }

  /**
   * Searches for all signatures in META-INF and applies them to checksum files.
   */
  private void verifyManifestAndLoadDigests(ZipFile zipFile, byte[] manifestBytes)
    throws IOException, CertificateException, NoSuchAlgorithmException, SignatureException {
    String META_INF = "META-INF/";

    ManifestDigester manifestDigester = new ManifestDigester(manifestBytes);
    Map<String, byte[]> signatureFiles = ContainerUtil.newHashMap();
    List<SignatureFileVerifier> pendingVerifiers = ContainerUtil.newArrayList();
    ArrayList<CodeSigner[]> signerCache = ContainerUtil.newArrayList();
    List<Object> manifestDigests = ContainerUtil.newArrayList();

    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      String name = zipEntry.getName();
      String nameUpper = name.toUpperCase();
      boolean interestingFile = nameUpper.startsWith(META_INF)
                                && nameUpper.indexOf('/', META_INF.length() + 1) < 0
                                && !nameUpper.endsWith("/MANIFEST.MF");
      if (interestingFile) {
        byte[] entryContents = consumeAndReadFully(zipFile.getInputStream(zipEntry));
        String pathWithoutExtension = name.substring(0, name.lastIndexOf('.'));
        if (nameUpper.endsWith(".SF")) {
          signatureFiles.put(pathWithoutExtension, entryContents);
          for (SignatureFileVerifier verifier : pendingVerifiers) {
            if (verifier.needSignatureFile(pathWithoutExtension)) {
              verifier.setSignatureFile(entryContents);
              verifier.process(myCodeSignersMap, manifestDigests);
            }
          }
        }
        else if (nameUpper.endsWith(".DSA") || nameUpper.endsWith(".RSA") || nameUpper.endsWith(".EC")) {
          SignatureFileVerifier verifier = new SignatureFileVerifier(signerCache, manifestDigester, name, entryContents);
          if (verifier.needSignatureFileBytes()) {
            byte[] signature = signatureFiles.get(pathWithoutExtension);
            if (signature == null) {
              pendingVerifiers.add(verifier);
            }
            else {
              verifier.setSignatureFile(signature);
              verifier.process(myCodeSignersMap, manifestDigests);
            }
          }
        }
      }
    }
    for (SignatureFileVerifier sfv : pendingVerifiers) {
      if (sfv.needSignatureFileBytes()) {
        throw new SecurityException("Some META-INF/ entries was not verified.");
      }
    }
  }

  private static MessageDigest createMessageDigest(String algorithm) {
    try {
      MessageDigest result;
      synchronized (ourPristineDigestsMonitor) {
        result = ourPristineDigests.get(algorithm);
        if (result == null) {
          result = MessageDigest.getInstance(algorithm);
          ourPristineDigests.put(algorithm, result);
        }
      }
      return (MessageDigest)result.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new IllegalStateException(e);
    }
    catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void loadDigests(@NotNull Manifest manifest) {
    // If got corrupted jar with two different checksums for the same file and algorithm
    // then SignatureFileVerifier will find this mismatch and will throw SecurityError.
    // So it is safe to take checksum only from MANIFEST.MF even if it contains broken one.
    String suffix = "-DIGEST";
    Map<String, byte[]> digestByAlgorithm = ContainerUtil.newHashMap();
    for (Map.Entry<String, Attributes> attributesEntry : manifest.getEntries().entrySet()) {
      digestByAlgorithm.clear();
      for (Map.Entry<Object, Object> attribute : attributesEntry.getValue().entrySet()) {
        assert attribute.getKey() instanceof Attributes.Name;
        String name = attribute.getKey().toString().toUpperCase();
        if (name.endsWith(suffix)) {
          String algorithm = name.substring(0, name.length() - suffix.length()).intern();
          byte[] newDigest = Base64.decode((String)attribute.getValue());
          digestByAlgorithm.put(algorithm, newDigest);
        }
      }
      myDigestByFileName.putValues(attributesEntry.getKey(), digestByAlgorithm.entrySet());
    }
  }

  @Override
  protected Resource instantiateResource(URL url, ZipEntry entry) throws IOException {
    return new MySecureResource(url, entry);
  }

  private class MySecureResource extends JarLoader.MyResource {
    MySecureResource(URL url, ZipEntry entry) throws IOException {
      super(url, entry);
    }

    @Override
    public byte[] getBytes() throws IOException {
      byte[] contents = super.getBytes();
      verifySignature(contents);
      myVerifiedEntries.add(myEntry.getName());
      return contents;
    }

    @Nullable
    @Override
    public CodeSigner[] getCodeSigners() {
      if (!myVerifiedEntries.contains(myEntry.getName())) {
        throw new IllegalStateException("Method `getBytes()` should be called first.");
      }
      return myCodeSignersMap.get(myEntry.getName());
    }

    @Override
    public boolean isSigned() {
      return true;
    }

    private void verifySignature(byte[] contents) {
      Collection<Map.Entry<String, byte[]>> digests = myDigestByFileName.get(myEntry.getName());
      for (Map.Entry<String, byte[]> digest : digests) {
        byte[] actualDigest = createMessageDigest(digest.getKey()).digest(contents);
        if (!MessageDigest.isEqual(actualDigest, digest.getValue())) {
          throw new SecurityException(digest.getKey() + " error for " + myEntry.getName());
        }
      }
    }
  }
}
