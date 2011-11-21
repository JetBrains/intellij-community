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
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidApkBuilder");

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

  public static Map<CompilerMessageCategory, List<String>> execute(Project project,
                                                                   @NotNull String resPackagePath,
                                                                   @NotNull String dexPath,
                                                                   @NotNull VirtualFile[] sourceRoots,
                                                                   @NotNull String[] externalJars,
                                                                   @NotNull VirtualFile[] nativeLibsFolders,
                                                                   @NotNull String finalApk,
                                                                   boolean unsigned,
                                                                   @NotNull String sdkPath,
                                                                   @Nullable String customKeystorePath) throws IOException {
    if (unsigned) {
      return filterUsingKeystoreMessages(
        finalPackage(project, dexPath, sourceRoots, externalJars, nativeLibsFolders, finalApk, resPackagePath, customKeystorePath, false));
    }

    final Map<CompilerMessageCategory, List<String>> map = new HashMap<CompilerMessageCategory, List<String>>();
    final String zipAlignPath = sdkPath + File.separator + AndroidUtils.toolPath(SdkConstants.FN_ZIPALIGN);
    boolean withAlignment = new File(zipAlignPath).exists();
    String unalignedApk = finalApk + UNALIGNED_SUFFIX;

    Map<CompilerMessageCategory, List<String>> map2 = filterUsingKeystoreMessages(
      finalPackage(project, dexPath, sourceRoots, externalJars, nativeLibsFolders, withAlignment ? unalignedApk : finalApk, resPackagePath,
                   customKeystorePath, true));
    map.putAll(map2);

    if (withAlignment && map.get(ERROR).size() == 0) {
      map2 = ExecutionUtil.execute(zipAlignPath, "-f", "4", unalignedApk, finalApk);
      map.putAll(map2);
    }
    return map;
  }

  private static Map<CompilerMessageCategory, List<String>> finalPackage(Project project,
                                                                         @NotNull String dexPath,
                                                                         @NotNull VirtualFile[] sourceRoots,
                                                                         @NotNull String[] externalJars,
                                                                         @NotNull VirtualFile[] nativeLibsFolders,
                                                                         @NotNull String outputApk, 
                                                                         @NotNull String apkPath,
                                                                         @Nullable String customKeystorePath,
                                                                         boolean signed) {
    final Map<CompilerMessageCategory, List<String>> result = new HashMap<CompilerMessageCategory, List<String>>();
    result.put(ERROR, new ArrayList<String>());
    result.put(INFORMATION, new ArrayList<String>());
    result.put(WARNING, new ArrayList<String>());

    FileOutputStream fos = null;
    try {

      String keyStoreOsPath = customKeystorePath != null && customKeystorePath.length() > 0
                              ? customKeystorePath 
                              : DebugKeyProvider.getDefaultKeyStoreOsPath();
      
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

      final HashSet<String> added = new HashSet<String>();
      for (VirtualFile sourceRoot : sourceRoots) {
        final HashSet<VirtualFile> sourceFolderResources = new HashSet<VirtualFile>();
        collectStandardSourceFolderResources(sourceRoot, new HashSet<VirtualFile>(), sourceFolderResources, project);
        writeStandardSourceFolderResources(sourceFolderResources, sourceRoot, builder, added);
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
        fis = new FileInputStream(externalJar);
        try {
          builder.writeZip(fis, filter);
        }
        finally {
          fis.close();
        }
      }

      final HashSet<String> nativeLibs = new HashSet<String>();
      for (VirtualFile nativeLibsFolder : nativeLibsFolders) {
        for (VirtualFile child : nativeLibsFolder.getChildren()) {
          writeNativeLibraries(builder, nativeLibsFolder, child, signed, nativeLibs);
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

  private static void writeNativeLibraries(SignedJarBuilder builder,
                                           VirtualFile nativeLibsFolder,
                                           VirtualFile child,
                                           boolean debugBuild,
                                           Set<String> added)
    throws IOException {
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
    collectNativeLibraries(child, list, debugBuild);
    for (VirtualFile file : list) {
      String relativePath = VfsUtilCore.getRelativePath(file, nativeLibsFolder, File.separatorChar);
      String path = FileUtil.toSystemIndependentName(SdkConstants.FD_APK_NATIVE_LIBS + File.separator + relativePath);
      if (added.add(path)) {
        builder.writeFile(toIoFile(file), path);
        LOG.info("Native lib file added to APK: " + file.getPath());
      }
      else {
        LOG.info("Duplicate in APK: native lib file " + file.getPath() + " won't be added.");
      }
    }
  }

  private static Map<CompilerMessageCategory, List<String>> addExceptionMessage(Exception e,
                                                                                Map<CompilerMessageCategory, List<String>> result) {
    LOG.info(e);
    String simpleExceptionName = e.getClass().getCanonicalName();
    result.get(ERROR).add(simpleExceptionName + ": " + e.getMessage());
    return result;
  }

  public static void collectNativeLibraries(@NotNull VirtualFile file, @NotNull List<VirtualFile> result, boolean debugBuild) {
    if (!file.isDirectory()) {
      String ext = file.getExtension();

      // some users store jars and *.so libs in the same directory. Do not pack JARs to APK's "lib" folder!
      if (AndroidUtils.EXT_NATIVE_LIB.equalsIgnoreCase(ext) ||
          (debugBuild && !(file.getFileType() instanceof ArchiveFileType))) {
        result.add(file);
      }
    }
    else if (JavaResourceFilter.checkFolderForPackaging(file.getName())) {
      for (VirtualFile child : file.getChildren()) {
        collectNativeLibraries(child, result, debugBuild);
      }
    }
  }

  public static void collectStandardSourceFolderResources(VirtualFile sourceFolder,
                                                          Set<VirtualFile> visited,
                                                          Set<VirtualFile> result,
                                                          @Nullable Project project) {
    visited.add(sourceFolder);
    
    for (VirtualFile child : sourceFolder.getChildren()) {
      if (child.exists()) {
        if (child.isDirectory()) {
          if (!visited.contains(child) &&
              JavaResourceFilter.checkFolderForPackaging(child.getName()) && !isExcludedFromCompilation(child, project)) {
            collectStandardSourceFolderResources(child, visited, result, project);
          }
        }
        else if (checkFileForPackaging(child) && !isExcludedFromCompilation(child, project)) {
          result.add(child);
        }
      }
    }
  }

  private static boolean isExcludedFromCompilation(VirtualFile child, @Nullable Project project) {
    final CompilerManager compilerManager = project != null ? CompilerManager.getInstance(project) : null;
    
    if (compilerManager == null) {
      return false;
    }
    
    if (!compilerManager.isExcludedFromCompilation(child)) {
      return false;
    }

    final Module module = ModuleUtil.findModuleForFile(child, project);
    if (module == null) {
      return true;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
      return true;
    }

    final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      return true;
    }

    // we exclude sources of library modules automatically for tools r7 or previous
    return platform.getSdk().getPlatformToolsRevision() > 7;
  }

  private static void writeStandardSourceFolderResources(Collection<VirtualFile> resources,
                                                         VirtualFile sourceRoot,
                                                         SignedJarBuilder jarBuilder,
                                                         Set<String> added) throws IOException {
    for (VirtualFile child : resources) {
      final String relativePath = FileUtil.toSystemIndependentName(VfsUtilCore.getRelativePath(child, sourceRoot, File.separatorChar));
      if (!added.contains(relativePath)) {
        File file = toIoFile(child);
        jarBuilder.writeFile(file, FileUtil.toSystemIndependentName(relativePath));
        added.add(relativePath);
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