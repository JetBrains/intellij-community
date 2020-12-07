// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assume;
import org.junit.Test;

import javax.crypto.KeyAgreement;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

public class SecureUrlClassLoaderTest {
  /**
   * IDEA's UrlClassLoader should verify JAR signatures and checksum if they are exists
   * but only if JAR url specified in {@link UrlClassLoader.Builder#pathsWithProtectionDomain}.
   */
  @Test
  public void testSignedJars() throws Exception {
    String className = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    URL classUrl = getJarUrl(className);

    ClassLoader classLoader;
    Exception error;

    classLoader = new URLClassLoader(new URL[]{classUrl}, null);
    error = codeThatRegistersSecurityProvider(classLoader, className);
    assertNull(error);

    classLoader = UrlClassLoader.build()
      .files(Collections.singletonList(Paths.get(classUrl.toURI())))
      .get();
    error = codeThatRegistersSecurityProvider(classLoader, className);

    // Oracle JRE prevents instantiating key exchange algorithm from unsigned JAR.
    // OpenJDK based runtimes including IntelliJ JDK does not prevent this.
    // Other JRE was not tested.
    if (!SystemInfo.isOracleJvm) {
      assertNull(error);
    }
    else {
      assertThat(error).isNotNull();
      assertEquals(SecurityException.class, error.getClass());
    }

    classLoader = UrlClassLoader.build()
      .files(Collections.singletonList(Paths.get(classUrl.toURI())))
      .urlsWithProtectionDomain(Collections.singleton(Paths.get(classUrl.toURI())))
      .get();
    error = codeThatRegistersSecurityProvider(classLoader, className);
    assertNull(error);
  }

  @NotNull
  private static URL getJarUrl(String className) throws MalformedURLException {
    URL classUrl = SecureUrlClassLoaderTest.class.getClassLoader().getResource(classNameToJarEntryName(className));
    assertEquals("jar", Objects.requireNonNull(classUrl).getProtocol());
    classUrl = new URL(classUrl.toExternalForm().split("[!]", 2)[0].substring("jar:".length()));
    return classUrl;
  }

  @NotNull
  private static String classNameToJarEntryName(@NotNull String className) {
    return className.replace('.', '/') + ".class";
  }

  @Nullable
  private static SecurityException codeThatRegistersSecurityProvider(ClassLoader classLoader, String className) {
    Class<?> providerClass;
    Provider provider;
    try {
      providerClass = classLoader.loadClass(className);
      provider = (Provider)providerClass.newInstance();
    }
    catch (Exception error) {
      throw new IllegalStateException(error);
    }
    Security.addProvider(provider);

    try {
      KeyAgreement.getInstance("DH", provider);
      return null;
    }
    catch (SecurityException error) {
      return error;
    }
    catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  /**
   * Method {@link #doTestLoadJarWithMaliciousThings(boolean, boolean, boolean, String)}
   * should create correct JAR when all change-parameters are set to false.
   */
  @Test
  public void testLoadCorrectJar() throws Exception {
    String className = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    SecurityException err = doTestLoadJarWithMaliciousThings(false, false, false, className);
    assertNull(err);
  }

  /**
   * Should not load class from JAR when class file was altered but no files in META-INF was not changed.
   */
  @Test
  public void testLoadJarWithMaliciousClass() throws Exception {
    String className = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    SecurityException err = doTestLoadJarWithMaliciousThings(true, false, false, className);
    assertNotNull(err);
    assertEquals("SHA-256 digest error for " + classNameToJarEntryName(className), err.getMessage());
  }

  /**
   * Should not load class from JAR when manifest file has incorrect signature of some class
   * but META-INF/*.SF contains correct signature of those class.
   */
  @Test
  public void testLoadJarWithMaliciousManifest() throws Exception {
    SecurityException err = doTestLoadJarWithMaliciousThings(false, true, false, "org.bouncycastle.jce.provider.BouncyCastleProvider");
    assertThat(err).hasMessageMatching(
      // This error message differs in Java 1.8 and Java 11.
      "[Ii]nvalid .*signature file digest for (Manifest main attributes|org/bouncycastle/jce/provider/BouncyCastleProvider.class)");
  }

  /**
   * Should not load class from JAR when class file was altered and its new digest
   * was written to manifest file.
   */
  @Test
  public void testLoadJarWithMaliciousClassAndManifest() throws Exception {
    SecurityException err = doTestLoadJarWithMaliciousThings(true, true, true, "org.bouncycastle.jce.provider.BouncyCastleProvider");
    assertNotNull(err);
    assertEquals("cannot verify signature block file META-INF/BC1024KE", err.getMessage());
  }

  /**
   * Should not load class from JAR when class file was altered and its new digest
   * was written to manifest file and all signature files.
   */
  @Test
  public void testLoadJarWithMaliciousClassAndManifestAndSignature() throws Exception {
    SecurityException err = doTestLoadJarWithMaliciousThings(true, true, true, "org.bouncycastle.jce.provider.BouncyCastleProvider");
    assertNotNull(err);
    assertEquals("cannot verify signature block file META-INF/BC1024KE", err.getMessage());
  }

  private static SecurityException doTestLoadJarWithMaliciousThings(boolean changeClass,
                                                                    boolean changeManifest,
                                                                    boolean changeSignatureFile,
                                                                    String className) throws Exception {
    File root = FileUtil.createTempDirectory("testLoadJarWithMaliciousClass", "");
    try {
      File hackedClassJar = new File(root, "hacked-bouncycastle.jar");
      URL classUrl = getJarUrl(className);
      Path hackedJarPath = hackedClassJar.toPath();

      createHackedJar(hackedClassJar, classUrl, className, changeClass, changeManifest, changeSignatureFile);

      ClassLoader classLoader = UrlClassLoader.build()
        .files(Collections.singletonList(hackedJarPath))
        .urlsWithProtectionDomain(Collections.singleton(hackedJarPath))
        .get();

      try {
        classLoader.loadClass(className);
        return null;
      }
      catch (SecurityException err) {
        return err;
      }
    }
    finally {
      FileUtil.delete(root);
    }
  }

  private static void createHackedJar(File destination,
                                      URL classUrl,
                                      String className,
                                      boolean changeClass,
                                      boolean changeManifest,
                                      boolean changeSignatureFile) throws Exception {
    String pathInJar = classNameToJarEntryName(className);
    try (ZipFile sourceZipFile = new ZipFile(new File(classUrl.getFile()), ZipFile.OPEN_READ)) {
      ByteArrayOutputStream hackedClassBytes = new ByteArrayOutputStream();
      Enumeration<? extends ZipEntry> entries = sourceZipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.getName().equals(pathInJar)) {
          StreamUtil.copy(sourceZipFile.getInputStream(entry), hackedClassBytes);
          break;
        }
      }
      Assume.assumeTrue(hackedClassBytes.size() > 0);

      // Adding one new byte is enough for changing hash digest of the file.
      // Doesn't matter that the class becomes invalid because
      // it should not be even tried to load.
      hackedClassBytes.write(0);

      try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destination))) {
        entries = sourceZipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();

          String upperEntryName = entry.getName().toUpperCase(Locale.US);
          boolean isManifest = upperEntryName.equals("META-INF/MANIFEST.MF");
          boolean isSignatureFile = upperEntryName.startsWith("META-INF/")
                                    && upperEntryName.indexOf('/', "META-INF/".length() + 1) < 0
                                    && upperEntryName.endsWith(".SF");
          boolean isDesiredClass = entry.getName().equals(pathInJar);

          if (isManifest && changeManifest || isSignatureFile && changeSignatureFile) {
            // Signature or manifest file can't be rewritten even if its
            // contents was not changed. `manifest.writeTo(InputStream)`
            // iterates over hashtable, therefore entries will be written
            // in unexpected order, that leads to false-positive
            // digital signature check failure.
            Manifest manifest;
            try (InputStream stream = sourceZipFile.getInputStream(entry)) {
              manifest = new Manifest(stream);
            }
            hackManifest(manifest, pathInJar, hackedClassBytes);
            zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
            manifest.write(zipOutputStream);
          }
          else if (isDesiredClass && changeClass) {
            zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
            hackedClassBytes.writeTo(zipOutputStream);
          }
          else {
            zipOutputStream.putNextEntry(entry);
            StreamUtil.copy(sourceZipFile.getInputStream(entry), zipOutputStream);
          }
          zipOutputStream.closeEntry();
        }
      }
    }
  }

  private static void hackManifest(Manifest manifest, String pathInJar, ByteArrayOutputStream hackedClassBytes) {
    Attributes newAttributes = new Attributes();
    byte[] digest = DigestUtil.sha256().digest(hackedClassBytes.toByteArray());
    newAttributes.putValue("SHA-256-Digest", Base64.getEncoder().encodeToString(digest));
    Assume.assumeTrue(manifest.getEntries().containsKey(pathInJar));
    manifest.getEntries().put(pathInJar, newAttributes);
  }
}