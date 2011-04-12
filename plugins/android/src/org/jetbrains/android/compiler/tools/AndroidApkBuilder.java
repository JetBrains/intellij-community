/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.compiler.tools;

import com.android.jarutils.DebugKeyProvider;
import com.android.jarutils.JavaResourceFilter;
import com.android.jarutils.SignedJarBuilder;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.intellij.openapi.compiler.CompilerMessageCategory.*;

/**
 * @author yole
 */
public class AndroidApkBuilder {
  private static final String UNALIGNED_SUFFIX = ".unaligned";

  private AndroidApkBuilder() {
  }

  private static Map<CompilerMessageCategory, List<String>> filterUsingKeystoreMessages(Map<CompilerMessageCategory, List<String>> messages) {
    List<String> infoMessages = messages.get(INFORMATION);
    if (infoMessages == null) {
      infoMessages = new ArrayList<String>();
      messages.put(INFORMATION, infoMessages);
    }
    final List<String> errors = messages.get(ERROR);
    for (Iterator<String> iterator = errors.iterator(); iterator.hasNext();) {
      String s = iterator.next();
      if (s.startsWith("Using keystore:")) {
        // not actually an error
        infoMessages.add(s);
        iterator.remove();
      }
    }
    return messages;
  }

  @SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
  private static void collectDuplicateEntries(@NotNull String rootFile, @NotNull Set<String> entries, @NotNull Set<String> result)
    throws IOException {
    final JavaResourceFilter javaResourceFilter = new JavaResourceFilter();

    FileInputStream fis = null;
    ZipInputStream zis = null;
    try {
      fis = new FileInputStream(rootFile);
      zis = new ZipInputStream(fis);

      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          String name = entry.getName();
          if (javaResourceFilter.checkEntry(name) && !entries.add(name)) {
            result.add(name);
          }
          zis.closeEntry();
        }
      }
    }
    finally {
      if (zis != null) {
        zis.close();
      }
      if (fis != null) {
        fis.close();
      }
    }
  }

  public static Map<CompilerMessageCategory, List<String>> execute(@NotNull String sdkPath,
                                                                   @NotNull String resPackagePath,
                                                                   @NotNull String dexPath,
                                                                   @NotNull VirtualFile[] sourceRoots,
                                                                   @NotNull String[] externalJars,
                                                                   @NotNull VirtualFile[] nativeLibsFolders,
                                                                   @NotNull String finalApk,
                                                                   boolean unsigned) throws IOException {
    if (unsigned) {
      return filterUsingKeystoreMessages(
        finalPackage(resPackagePath, dexPath, sourceRoots, externalJars, nativeLibsFolders, finalApk, false));
    }

    final Map<CompilerMessageCategory, List<String>> map = new HashMap<CompilerMessageCategory, List<String>>();
    final String zipAlignPath = sdkPath + File.separator + AndroidUtils.toolPath(SdkConstants.FN_ZIPALIGN);
    boolean withAlignment = new File(zipAlignPath).exists();
    String unalignedApk = finalApk + UNALIGNED_SUFFIX;

    Map<CompilerMessageCategory, List<String>> map2 = filterUsingKeystoreMessages(
      finalPackage(resPackagePath, dexPath, sourceRoots, externalJars, nativeLibsFolders, withAlignment ? unalignedApk : finalApk, true));
    map.putAll(map2);

    if (withAlignment && map.get(ERROR).size() == 0) {
      map2 = ExecutionUtil.execute(zipAlignPath, "-f", "4", unalignedApk, finalApk);
      map.putAll(map2);
    }
    return map;
  }

  private static Map<CompilerMessageCategory, List<String>> finalPackage(@NotNull String apkPath,
                                                                         @NotNull String dexPath,
                                                                         @NotNull VirtualFile[] sourceRoots,
                                                                         @NotNull String[] externalJars,
                                                                         @NotNull VirtualFile[] nativeLibsFolders,
                                                                         @NotNull String outputApk,
                                                                         boolean signed) {
    final Map<CompilerMessageCategory, List<String>> result = new HashMap<CompilerMessageCategory, List<String>>();
    result.put(ERROR, new ArrayList<String>());
    result.put(INFORMATION, new ArrayList<String>());
    result.put(WARNING, new ArrayList<String>());

    FileOutputStream fos = null;
    try {

      String keyStoreOsPath = DebugKeyProvider.getDefaultKeyStoreOsPath();
      DebugKeyProvider provider = createDebugKeyProvider(result, keyStoreOsPath);

      X509Certificate certificate = signed ? (X509Certificate)provider.getCertificate() : null;

      if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
        // generate a new one
        File keyStoreFile = new File(keyStoreOsPath);
        if (keyStoreFile.exists()) {
          keyStoreFile.delete();
        }
        provider = createDebugKeyProvider(result, keyStoreOsPath);
        certificate = (X509Certificate)provider.getCertificate();
      }

      if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
        String date = DateFormatUtil.formatPrettyDateTime(certificate.getNotAfter());
        result.get(ERROR).add(AndroidBundle.message("android.debug.certificate.expired.error", date, keyStoreOsPath));
        return result;
      }

      PrivateKey key = provider.getDebugKey();

      if (key == null) {
        result.get(ERROR).add(AndroidBundle.message("android.cannot.create.new.key.error"));
        return result;
      }

      if (!new File(apkPath).exists()) {
        result.get(CompilerMessageCategory.ERROR).add("File " + apkPath + " not found. Try to rebuild project");
        return result;
      }

      File dexEntryFile = new File(dexPath);
      if (!dexEntryFile.exists()) {
        result.get(CompilerMessageCategory.ERROR).add("File " + dexEntryFile.getPath() + " not found. Try to rebuild project");
        return result;
      }

      for (String externalJar : externalJars) {
        if (new File(externalJar).isDirectory()) {
          result.get(CompilerMessageCategory.ERROR).add(externalJar + " is directory. Directory libraries are not supported");
        }
      }

      if (result.get(CompilerMessageCategory.ERROR).size() > 0) {
        return result;
      }

      fos = new FileOutputStream(outputApk);
      SignedJarBuilder builder = new SignedJarBuilder(fos, key, certificate);

      FileInputStream fis = new FileInputStream(apkPath);
      try {
        builder.writeZip(fis, null);
      }
      finally {
        fis.close();
      }

      builder.writeFile(dexEntryFile, AndroidUtils.CLASSES_FILE_NAME);
      for (VirtualFile sourceRoot : sourceRoots) {
        writeStandardSourceFolderResources(builder, sourceRoot, sourceRoot, new HashSet<VirtualFile>(), new HashSet<String>());
      }

      Set<String> duplicates = new HashSet<String>();
      Set<String> entries = new HashSet<String>();
      for (String externalJar : externalJars) {
        collectDuplicateEntries(externalJar, entries, duplicates);
      }

      for (String duplicate : duplicates) {
        result.get(CompilerMessageCategory.WARNING).add("Duplicate entry " + duplicate + ". The file won't be added");
      }

      MyResourceFilter filter = new MyResourceFilter(duplicates);

      for (String externalJar : externalJars) {
        try {
          fis = new FileInputStream(externalJar);
          builder.writeZip(fis, filter);
        }
        finally {
          fis.close();
        }
      }
      for (VirtualFile nativeLibsFolder : nativeLibsFolders) {
        for (VirtualFile child : nativeLibsFolder.getChildren()) {
          writeNativeLibraries(builder, nativeLibsFolder, child);
        }
      }
      builder.close();
    }
    catch (IOException e) {
      return addExceptionMessage(e, result);
    }
    catch (CertificateException e) {
      return addExceptionMessage(e, result);
    }
    catch (DebugKeyProvider.KeytoolException e) {
      return addExceptionMessage(e, result);
    }
    catch (AndroidLocation.AndroidLocationException e) {
      return addExceptionMessage(e, result);
    }
    catch (NoSuchAlgorithmException e) {
      return addExceptionMessage(e, result);
    }
    catch (UnrecoverableEntryException e) {
      return addExceptionMessage(e, result);
    }
    catch (KeyStoreException e) {
      return addExceptionMessage(e, result);
    }
    catch (GeneralSecurityException e) {
      return addExceptionMessage(e, result);
    }
    finally {
      if (fos != null) {
        try {
          fos.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    return result;
  }

  private static DebugKeyProvider createDebugKeyProvider(final Map<CompilerMessageCategory, List<String>> result, String path) throws
                                                                                                                               KeyStoreException,
                                                                                                                               NoSuchAlgorithmException,
                                                                                                                               CertificateException,
                                                                                                                               UnrecoverableEntryException,
                                                                                                                               IOException,
                                                                                                                               DebugKeyProvider.KeytoolException,
                                                                                                                               AndroidLocation.AndroidLocationException {

    return new DebugKeyProvider(path, null, new DebugKeyProvider.IKeyGenOutput() {
      public void err(String message) {
        result.get(ERROR).add("Error during key creation: " + message);
      }

      public void out(String message) {
        result.get(INFORMATION).add("Info message during key creation: " + message);
      }
    });
  }

  private static void writeNativeLibraries(SignedJarBuilder builder, VirtualFile nativeLibsFolder, VirtualFile child) throws IOException {
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
    collectNativeLibraries(child, list);
    for (VirtualFile file : list) {
      String relativePath = VfsUtil.getRelativePath(file, nativeLibsFolder, File.separatorChar);
      String path = FileUtil.toSystemIndependentName(SdkConstants.FD_APK_NATIVE_LIBS + File.separator + relativePath);
      builder.writeFile(toIoFile(file), path);
    }
  }

  private static Map<CompilerMessageCategory, List<String>> addExceptionMessage(Exception e,
                                                                                Map<CompilerMessageCategory, List<String>> result) {
    String simpleExceptionName = e.getClass().getCanonicalName();
    result.get(ERROR).add(simpleExceptionName + ": " + e.getMessage());
    return result;
  }

  public static void collectNativeLibraries(@NotNull VirtualFile file, @NotNull List<VirtualFile> result) {
    if (!file.isDirectory()) {
      String ext = file.getExtension();
      if (AndroidUtils.EXT_NATIVE_LIB.equalsIgnoreCase(ext)) {
        result.add(file);
      }
    }
    else if (JavaResourceFilter.checkFolderForPackaging(file.getName())) {
      for (VirtualFile child : file.getChildren()) {
        collectNativeLibraries(child, result);
      }
    }
  }

  private static void writeStandardSourceFolderResources(SignedJarBuilder jarBuilder,
                                                         @NotNull VirtualFile sourceRoot,
                                                         @NotNull VirtualFile sourceFolder,
                                                         @NotNull Set<VirtualFile> visited,
                                                         @NotNull Set<String> added) throws IOException {
    visited.add(sourceFolder);
    for (VirtualFile child : sourceFolder.getChildren()) {
      if (child.exists()) {
        if (child.isDirectory()) {
          if (!visited.contains(child) && JavaResourceFilter.checkFolderForPackaging(child.getName())) {
            writeStandardSourceFolderResources(jarBuilder, sourceRoot, child, visited, added);
          }
        }
        else if (checkFileForPackaging(child)) {
          String relativePath = FileUtil.toSystemIndependentName(VfsUtil.getRelativePath(child, sourceRoot, File.separatorChar));
          if (relativePath != null && !added.contains(relativePath)) {
            File file = toIoFile(child);
            jarBuilder.writeFile(file, FileUtil.toSystemIndependentName(relativePath));
            added.add(relativePath);
          }
        }
      }
    }
  }

  private static File toIoFile(VirtualFile child) {
    return new File(FileUtil.toSystemDependentName(child.getPath())).getAbsoluteFile();
  }

  private static boolean checkFileForPackaging(VirtualFile file) {
    String fileName = file.getNameWithoutExtension();
    if (fileName.length() > 0) {
      return JavaResourceFilter.checkFileForPackaging(fileName, file.getExtension());
    }
    return false;
  }

  private static class MyResourceFilter extends JavaResourceFilter {
    private final Set<String> myExcludedEntries;

    public MyResourceFilter(@NotNull Set<String> excludedEntries) {
      myExcludedEntries = excludedEntries;
    }

    @Override
    public boolean checkEntry(String name) {
      if (myExcludedEntries.contains(name)) {
        return false;
      }
      return super.checkEntry(name);
    }
  }
}