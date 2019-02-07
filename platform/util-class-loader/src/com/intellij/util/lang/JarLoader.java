// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.reference.SoftReference;
import com.intellij.util.Base64;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.util.ManifestDigester;
import sun.security.util.SignatureFileVerifier;

import java.io.*;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.openapi.util.Pair.pair;

class JarLoader extends Loader {
  private static final List<Pair<Resource.Attribute, Attributes.Name>> PACKAGE_FIELDS = Arrays.asList(
    pair(Resource.Attribute.SPEC_TITLE, Attributes.Name.SPECIFICATION_TITLE),
    pair(Resource.Attribute.SPEC_VERSION, Attributes.Name.SPECIFICATION_VERSION),
    pair(Resource.Attribute.SPEC_VENDOR, Attributes.Name.SPECIFICATION_VENDOR),
    pair(Resource.Attribute.IMPL_TITLE, Attributes.Name.IMPLEMENTATION_TITLE),
    pair(Resource.Attribute.IMPL_VERSION, Attributes.Name.IMPLEMENTATION_VERSION),
    pair(Resource.Attribute.IMPL_VENDOR, Attributes.Name.IMPLEMENTATION_VENDOR));

  private static final Map<String, MessageDigest> ourPristineDigests = ContainerUtil.newHashMap();
  private static final Object ourPristineDigestsMonitor = new Object();

  private final String myFilePath;
  private final ClassPath myConfiguration;
  private final URL myUrl;
  @Nullable private final MultiMap<String, Map.Entry<String, byte[]>> myDigestByFileName;

  /**
   * Map from file name inside ZIP archive to collection of code signers.
   * Implementation via Hashtable is required by {@link SignatureFileVerifier}.
   */
  @Nullable private Hashtable<String, CodeSigner[]> myCodeSignersMap;
  @Nullable private Set<String> myVerifiedEntries;
  private SoftReference<JarMemoryLoader> myMemoryLoader;
  private volatile SoftReference<ZipFile> myZipFileSoftReference; // Used only when myConfiguration.myCanLockJars==true
  private volatile Map<Resource.Attribute, String> myAttributes;
  private volatile String myClassPathManifestAttribute;
  private static final String NULL_STRING = "<null>";

  JarLoader(URL url, int index, ClassPath configuration, boolean secure) throws IOException {
    super(new URL("jar", "", -1, url + "!/"), index);

    myFilePath = urlToFilePath(url);
    myConfiguration = configuration;
    myUrl = url;

    if (!configuration.myLazyClassloadingCaches) {
      ZipFile zipFile = getZipFile(); // IOException from opening is propagated to caller if zip file isn't valid,
      try {
        if (configuration.myPreloadJarContents) {
          JarMemoryLoader loader = JarMemoryLoader.load(zipFile, getBaseURL(), this);
          if (loader != null) {
            myMemoryLoader = new SoftReference<JarMemoryLoader>(loader);
          }
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }

    if (secure) {
      myCodeSignersMap = new Hashtable<String, CodeSigner[]>();
      myDigestByFileName = MultiMap.createSmart();
      myVerifiedEntries = ContainerUtil.newHashSet();
    }
    else {
      myCodeSignersMap = null;
      myDigestByFileName = null;
      myVerifiedEntries = null;
    }
  }

  Map<Resource.Attribute, String> getAttributes() {
    loadManifestAttributes();
    return myAttributes;
  }

  @Nullable
  String getClassPathManifestAttribute() {
    loadManifestAttributes();
    String manifestAttribute = myClassPathManifestAttribute;
    return manifestAttribute != NULL_STRING ? manifestAttribute : null;
  }

  private static String urlToFilePath(URL url) {
    try {
      return new File(url.toURI()).getPath();
    } catch (Throwable ignore) { // URISyntaxException or IllegalArgumentException
      return url.getPath();
    }
  }

  @Nullable
  private static Map<Resource.Attribute, String> getAttributes(@Nullable Attributes attributes) {
    if (attributes == null) return null;
    Map<Resource.Attribute, String> map = null;

    for (Pair<Resource.Attribute, Attributes.Name> p : PACKAGE_FIELDS) {
      String value = attributes.getValue(p.second);
      if (value != null) {
        if (map == null) map = new EnumMap<Resource.Attribute, String>(Resource.Attribute.class);
        map.put(p.first, value);
      }
    }

    return map;
  }

  private void loadManifestAttributes() {
    if (myClassPathManifestAttribute != null) return;
    synchronized (this) {
      try {
        if (myClassPathManifestAttribute != null) return;
        ZipFile zipFile = getZipFile();
        try {
          Attributes manifestAttributes = myConfiguration.getManifestData(myUrl);
          if (manifestAttributes == null) {
            ZipEntry entry = zipFile.getEntry(JarFile.MANIFEST_NAME);
            boolean digestsRequired = myDigestByFileName != null;
            Manifest manifest;
            InputStream zipEntryStream = entry != null ? zipFile.getInputStream(entry) : null;
            if (digestsRequired) {
              manifest = loadManifestSecurely(zipFile, zipEntryStream);
              if (manifest != null) {
                loadDigests(manifest);
                manifestAttributes = manifest.getMainAttributes();
              }
            }
            else {
              manifestAttributes = loadManifestAttributes(zipEntryStream);
            }
            if (manifestAttributes == null) manifestAttributes = new Attributes(0);
            myConfiguration.cacheManifestData(myUrl, manifestAttributes);
          }

          myAttributes = getAttributes(manifestAttributes);
          Object attribute = manifestAttributes.get(Attributes.Name.CLASS_PATH);
          myClassPathManifestAttribute = attribute instanceof String ? (String)attribute : NULL_STRING;
        }
        finally {
          releaseZipFile(zipFile);
        }
      } catch (IOException io) {
        throw new RuntimeException(io);
      }
    }
  }

  @Nullable
  private static Attributes loadManifestAttributes(@Nullable InputStream stream) {
    if (stream == null) return null;
    try {
      try {
        return new Manifest(stream).getMainAttributes();
      }
      finally {
        stream.close();
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  /**
   * Loads MANIFEST.MF and checks that it was correctly signed.
   *
   * @param zipFile        ZipFile instance.
   * @param stream         Opened stream for MANIFEST.MF. Must be closed outside after execution.
   */
  @Nullable
  private Manifest loadManifestSecurely(ZipFile zipFile, InputStream stream) {
    if (stream == null) return null;
    byte[] manifestBytes;
    try {
      manifestBytes = consumeAndReadFully(stream);
      verifyManifestAndLoadDigests(zipFile, manifestBytes);
      return new Manifest(new ByteArrayInputStream(manifestBytes));
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
  }

  private static byte[] consumeAndReadFully(@NotNull InputStream stream) throws IOException {
    try {
      return FileUtil.loadBytes(stream);
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

  private void loadDigests(@NotNull Manifest manifest) {
    // If got corrupted jar with two different checksums for the same file and algorithm
    // then SignatureFileVerifier will find this mismatch and will throw SecurityError.
    // So it is safe to take checksum only from MANIFEST.MF even if it contains broken one.
    assert myDigestByFileName != null;
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

  @NotNull
  @Override
  public ClasspathCache.LoaderData buildData() throws IOException {
    ZipFile zipFile = getZipFile();
    try {
      ClasspathCache.LoaderDataBuilder loaderDataBuilder = new ClasspathCache.LoaderDataBuilder();
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();

        if (name.endsWith(UrlClassLoader.CLASS_EXTENSION)) {
          loaderDataBuilder.addClassPackageFromName(name);
        } else {
          loaderDataBuilder.addResourcePackageFromName(name);
        }

        loaderDataBuilder.addPossiblyDuplicateNameEntry(name);
      }

      return loaderDataBuilder.build();
    }
    finally {
      releaseZipFile(zipFile);
    }
  }

  private final AtomicInteger myNumberOfRequests = new AtomicInteger();
  private volatile TIntHashSet myPackageHashesInside;

  private TIntHashSet buildPackageHashes() {
    try {
      ZipFile zipFile = getZipFile();
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        TIntHashSet result = new TIntHashSet();

        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          result.add(ClasspathCache.getPackageNameHash(entry.getName()));
        }
        result.add(0); // empty package is in every jar
        return result;
      }
      finally {
        releaseZipFile(zipFile);
      }
    } catch (Exception e) {
      error("url: " + myFilePath, e);
      return new TIntHashSet(0);
    }
  }

  @Override
  @Nullable
  Resource getResource(String name) {
    if (myConfiguration.myLazyClassloadingCaches) {
      int numberOfHits = myNumberOfRequests.incrementAndGet();
      TIntHashSet packagesInside = myPackageHashesInside;

      if (numberOfHits > ClasspathCache.NUMBER_OF_ACCESSES_FOR_LAZY_CACHING && packagesInside == null) {
        myPackageHashesInside = packagesInside = buildPackageHashes();
      }

      if (packagesInside != null && !packagesInside.contains(ClasspathCache.getPackageNameHash(name))) {
        return null;
      }
    }

    JarMemoryLoader loader = myMemoryLoader != null ? myMemoryLoader.get() : null;
    if (loader != null) {
      Resource resource = loader.getResource(name);
      if (resource != null) return resource;
    }

    try {
      ZipFile zipFile = getZipFile();
      try {
        ZipEntry entry = zipFile.getEntry(name);
        if (entry != null) {
          return new MyResource(getBaseURL(), entry);
        }
      }
      finally {
        releaseZipFile(zipFile);
      }
    }
    catch (Exception e) {
      error("url: " + myFilePath, e);
    }

    return null;
  }

  private class MyResource extends Resource {
    private final URL myUrl;
    private final ZipEntry myEntry;

    MyResource(URL url, ZipEntry entry) throws IOException {
      myUrl = new URL(url, entry.getName());
      myEntry = entry;
    }

    @Override
    public URL getURL() {
      return myUrl;
    }

    @Override
    public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(getBytes());
    }

    @Override
    public byte[] getBytes() throws IOException {
      ZipFile file = getZipFile();
      InputStream stream = null;
      try {
        stream = file.getInputStream(myEntry);
        byte[] contents = FileUtilRt.loadBytes(stream, (int)myEntry.getSize());
        if (myDigestByFileName != null) {
          verifySignature(contents);
          assert myVerifiedEntries != null;
          myVerifiedEntries.add(myEntry.getName());
        }
        return contents;
      } finally {
        if (stream != null) stream.close();
        releaseZipFile(file);
      }
    }

    @Nullable
    @Override
    public CodeSigner[] getCodeSigners() {
      if (myCodeSignersMap != null) {
        assert myVerifiedEntries != null;
        if (!myVerifiedEntries.contains(myEntry.getName())) {
          throw new IllegalStateException("Method `getBytes()` should be called first.");
        }
        return myCodeSignersMap.get(myEntry.getName());
      }
      return null;
    }

    @Override
    public boolean isSigned() {
      return myCodeSignersMap != null;
    }

    private void verifySignature(byte[] contents) {
      assert myDigestByFileName != null;
      Collection<Map.Entry<String, byte[]>> digests = myDigestByFileName.get(myEntry.getName());
      for (Map.Entry<String, byte[]> digest : digests) {
        byte[] actualDigest = createMessageDigest(digest.getKey()).digest(contents);
        if (!MessageDigest.isEqual(actualDigest, digest.getValue())) {
          throw new SecurityException(digest.getKey() + " error for " + myEntry.getName());
        }
      }
    }

    @Override
    public String getValue(Attribute key) {
      loadManifestAttributes();
      return myAttributes != null ? myAttributes.get(key) : null;
    }
  }

  protected void error(String message, Throwable t) {
    if (myConfiguration.myLogErrorOnMissingJar) {
      LoggerRt.getInstance(JarLoader.class).error(message, t);
    }
    else {
      LoggerRt.getInstance(JarLoader.class).warn(message, t);
    }
  }

  private static final Object ourLock = new Object();

  @NotNull
  private ZipFile getZipFile() throws IOException {
    // This code is executed at least 100K times (O(number of classes needed to load)) and it takes considerable time to open ZipFile's
    // such number of times so we store reference to ZipFile if we allowed to lock the file (assume it isn't changed)
    if (myConfiguration.myCanLockJars) {
      ZipFile zipFile = SoftReference.dereference(myZipFileSoftReference);
      if (zipFile != null) return zipFile;

      synchronized (ourLock) {
        zipFile = SoftReference.dereference(myZipFileSoftReference);
        if (zipFile != null) return zipFile;

        // ZipFile's native implementation (ZipFile.c, zip_util.c) has path -> file descriptor cache
        zipFile = new ZipFile(myFilePath);
        myZipFileSoftReference = new SoftReference<ZipFile>(zipFile);
        return zipFile;
      }
    }
    else {
      return new ZipFile(myFilePath);
    }
  }

  private void releaseZipFile(ZipFile zipFile) throws IOException {
    // Closing of zip file when myConfiguration.myCanLockJars=true happens in ZipFile.finalize
    if (!myConfiguration.myCanLockJars) {
      zipFile.close();
    }
  }

  @Override
  public String toString() {
    return "JarLoader [" + myFilePath + "]";
  }
}
