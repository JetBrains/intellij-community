/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.util;

import com.android.jarutils.SignedJarBuilder;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.AndroidCommonBundle;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidCommonUtils");

  @NonNls public static final String MANIFEST_JAVA_FILE_NAME = "Manifest.java";
  @NonNls public static final String R_JAVA_FILENAME = "R.java";
  @NonNls public static final String CLASSES_JAR_FILE_NAME = "classes.jar";
  @NonNls public static final String CLASSES_FILE_NAME = "classes.dex";
  private static final Pattern WARNING_PATTERN = Pattern.compile(".*warning.*");
  private static final Pattern ERROR_PATTERN = Pattern.compile(".*error.*");
  private static final Pattern EXCEPTION_PATTERN = Pattern.compile(".*exception.*");

  public static final Pattern R_PATTERN = Pattern.compile("R(\\$.*)?\\.class");
  private static final Pattern MANIFEST_PATTERN = Pattern.compile("Manifest(\\$.*)?\\.class");
  private static final String BUILD_CONFIG_CLASS_NAME = "BuildConfig.class";

  public static final Pattern COMPILER_MESSAGE_PATTERN = Pattern.compile("(.+):(\\d+):.+");

  @NonNls public static final String PNG_EXTENSION = "png";
  private static final String[] DRAWABLE_EXTENSIONS = new String[]{PNG_EXTENSION, "jpg", "gif"};

  @NonNls public static final String RELEASE_BUILD_OPTION = "RELEASE_BUILD_KEY";
  @NonNls public static final String PROGUARD_CFG_PATH_OPTION = "ANDROID_PROGUARD_CFG_PATH";
  @NonNls public static final String DIRECTORY_FOR_LOGS_NAME = "proguard_logs";
  @NonNls public static final String PROGUARD_OUTPUT_JAR_NAME = "obfuscated_sources.jar";
  @NonNls public static final String INCLUDE_SYSTEM_PROGUARD_FILE_OPTION = "INCLUDE_SYSTEM_PROGUARD_FILE";
  @NonNls public static final String SYSTEM_PROGUARD_CFG_FILE_NAME = "proguard-android.txt";
  @NonNls private static final String PROGUARD_HOME_ENV_VARIABLE = "PROGUARD_HOME";
  public static final ResourceType[] ID_PROVIDING_RESOURCE_TYPES = new ResourceType[] {
    ResourceType.LAYOUT, ResourceType.MENU
  };

  @NonNls public static final String INCLUDE_SYSTEM_PROGUARD_FILE_ELEMENT_NAME = "includeSystemProguardFile";
  @NonNls public static final String INCLUDE_ASSETS_FROM_LIBRARIES_ELEMENT_NAME = "includeAssetsFromLibraries";
  @NonNls public static final String ADDITIONAL_NATIVE_LIBS_ELEMENT = "additionalNativeLibs";
  @NonNls public static final String ITEM_ELEMENT = "item";
  @NonNls public static final String ARCHITECTURE_ATTRIBUTE = "architecture";
  @NonNls public static final String URL_ATTRIBUTE = "url";
  @NonNls public static final String TARGET_FILE_NAME_ATTRIBUTE = "targetFileName";

  private static final String[] TEST_CONFIGURATION_TYPE_IDS =
    {"JUnit", "TestNG", "ScalaTestRunConfiguration", "SpecsRunConfiguration", "Specs2RunConfiguration"};
  @NonNls public static final String ANNOTATIONS_JAR_RELATIVE_PATH = "/tools/support/annotations.jar";

  @NonNls public static final String PACKAGE_MANIFEST_ATTRIBUTE = "package";

  @NonNls public static final String ANDROID_FINAL_PACKAGE_FOR_ARTIFACT_SUFFIX = ".afp";
  @NonNls public static final String ANDROID_MANIFEST_MERGER_PROPERTY = "manifestmerger.enabled";
  @NonNls public static final String PROGUARD_CFG_OUTPUT_FILE_NAME = "proguard.txt";

  @NonNls public static final String DEX_BUILD_TARGET_TYPE_ID = "android-dex";
  @NonNls public static final String PACKAGING_BUILD_TARGET_TYPE_ID = "android-packaging";

  private AndroidCommonUtils() {
  }

  public static boolean isTestConfiguration(@NotNull String typeId) {
    return ArrayUtil.find(TEST_CONFIGURATION_TYPE_IDS, typeId) >= 0;
  }

  public static String command2string(@NotNull Collection<String> command) {
    final StringBuilder builder = new StringBuilder();
    for (Iterator<String> it = command.iterator(); it.hasNext(); ) {
      String s = it.next();
      builder.append('[');
      builder.append(s);
      builder.append(']');
      if (it.hasNext()) {
        builder.append(' ');
      }
    }
    return builder.toString();
  }

  @Nullable
  public static SdkManager createSdkManager(@NotNull String path, @NotNull ISdkLog log) {
    path = FileUtil.toSystemDependentName(path);

    final File f = new File(path);
    if (!f.exists() || !f.isDirectory()) {
      return null;
    }

    final File platformsDir = new File(f, SdkConstants.FD_PLATFORMS);
    if (!platformsDir.exists() || !platformsDir.isDirectory()) {
      return null;
    }

    // safety from errors inside sdklib
    try {
      return SdkManager.createManager(path + File.separatorChar, log);
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static void moveAllFiles(@NotNull File from, @NotNull File to, @NotNull Collection<File> newFiles) throws IOException {
    if (from.isFile()) {
      FileUtil.rename(from, to);
      newFiles.add(to);
    }
    else {
      final File[] children = from.listFiles();

      if (children != null) {
        for (File child : children) {
          moveAllFiles(child, new File(to, child.getName()), newFiles);
        }
      }
    }
  }

  public static void handleDexCompilationResult(@NotNull Process process,
                                                @NotNull String outputFilePath,
                                                @NotNull final Map<AndroidCompilerMessageKind, List<String>> messages) {
    final BaseOSProcessHandler handler = new BaseOSProcessHandler(process, null, null);
    handler.addProcessListener(new ProcessAdapter() {
      private AndroidCompilerMessageKind myCategory = null;

      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        String[] msgs = event.getText().split("\\n");
        for (String msg : msgs) {
          msg = msg.trim();
          String msglc = msg.toLowerCase();
          if (outputType == ProcessOutputTypes.STDERR) {
            if (WARNING_PATTERN.matcher(msglc).matches()) {
              myCategory = AndroidCompilerMessageKind.WARNING;
            }
            if (ERROR_PATTERN.matcher(msglc).matches() || EXCEPTION_PATTERN.matcher(msglc).matches() || myCategory == null) {
              myCategory = AndroidCompilerMessageKind.ERROR;
            }
            messages.get(myCategory).add(msg);
          }
          else if (outputType == ProcessOutputTypes.STDOUT) {
            if (!msglc.startsWith("processing")) {
              messages.get(AndroidCompilerMessageKind.INFORMATION).add(msg);
            }
          }

          LOG.debug(msg);
        }
      }
    });

    handler.startNotify();
    handler.waitFor();

    final List<String> errors = messages.get(AndroidCompilerMessageKind.ERROR);

    if (new File(outputFilePath).isFile()) {
      // if compilation finished correctly, show all errors as warnings
      messages.get(AndroidCompilerMessageKind.WARNING).addAll(errors);
      errors.clear();
    }
    else if (errors.size() == 0) {
      errors.add("Cannot create classes.dex file");
    }
  }

  public static void packClassFilesIntoJar(@NotNull String[] firstPackageDirPaths,
                                           @NotNull String[] libFirstPackageDirPaths,
                                           @NotNull File jarFile) throws IOException {
    final List<Pair<File, String>> files = new ArrayList<Pair<File, String>>();
    for (String path : firstPackageDirPaths) {
      final File firstPackageDir = new File(path);
      if (firstPackageDir.exists()) {
        packClassFilesIntoJar(firstPackageDir, firstPackageDir.getParentFile(), true, files);
      }
    }

    for (String path : libFirstPackageDirPaths) {
      final File firstPackageDir = new File(path);
      if (firstPackageDir.exists()) {
        packClassFilesIntoJar(firstPackageDir, firstPackageDir.getParentFile(), false, files);
      }
    }

    if (files.size() > 0) {
      final JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile));
      try {
        for (Pair<File, String> pair : files) {
          packIntoJar(jos, pair.getFirst(), pair.getSecond());
        }
      }
      finally {
        jos.close();
      }
    }
    else if (jarFile.isFile()) {
      if (!jarFile.delete()) {
        throw new IOException("Cannot delete file " + FileUtil.toSystemDependentName(jarFile.getPath()));
      }
    }
  }

  private static void packClassFilesIntoJar(@NotNull File file,
                                            @NotNull File rootDirectory,
                                            boolean packRAndManifestClasses,
                                            @NotNull List<Pair<File, String>> files)
    throws IOException {

    if (file.isDirectory()) {
      final File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          packClassFilesIntoJar(child, rootDirectory, packRAndManifestClasses, files);
        }
      }
    }
    else if (file.isFile()) {
      if (!FileUtil.getExtension(file.getName()).equals("class")) {
        return;
      }

      if (!packRAndManifestClasses &&
          (R_PATTERN.matcher(file.getName()).matches() ||
           MANIFEST_PATTERN.matcher(file.getName()).matches() ||
           BUILD_CONFIG_CLASS_NAME.equals(file.getName()))) {
        return;
      }

      final String rootPath = rootDirectory.getAbsolutePath();

      String path = file.getAbsolutePath();
      path = FileUtil.toSystemIndependentName(path.substring(rootPath.length()));
      if (path.charAt(0) == '/') {
        path = path.substring(1);
      }

      files.add(new Pair<File, String>(file, path));
    }
  }

  private static void packIntoJar(@NotNull JarOutputStream jar, @NotNull File file, @NotNull String path) throws IOException {
    final JarEntry entry = new JarEntry(path);
    entry.setTime(file.lastModified());
    jar.putNextEntry(entry);

    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
    try {
      final byte[] buffer = new byte[1024];
      int count;
      while ((count = bis.read(buffer)) != -1) {
        jar.write(buffer, 0, count);
      }
      jar.closeEntry();
    }
    finally {
      bis.close();
    }
  }

  @Nullable
  public static String getResourceTypeByDirName(@NotNull String name) {
    final int index = name.indexOf('-');
    final String type = index >= 0 ? name.substring(0, index) : name;
    return ResourceFolderType.getTypeByName(type) != null ? type : null;
  }

  @NotNull
  public static String getResourceName(@NotNull String resourceType, @NotNull String fileName) {
    final String extension = FileUtil.getExtension(fileName);
    final String s = FileUtil.getNameWithoutExtension(fileName);

    return resourceType.equals("drawable") &&
           ArrayUtil.find(DRAWABLE_EXTENSIONS, extension) >= 0 &&
           s.endsWith(".9") &&
           extension.equals(PNG_EXTENSION)
           ? s.substring(0, s.length() - 2)
           : s;
  }

  @NotNull
  public static String getResourceTypeByTagName(@NotNull String tagName) {
    if (tagName.equals("declare-styleable")) {
      tagName = "styleable";
    }
    else if (tagName.endsWith("-array")) {
      tagName = "array";
    }
    return tagName;
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> launchProguard(@NotNull IAndroidTarget target,
                                                                             int sdkToolsRevision,
                                                                             @NotNull String sdkOsPath,
                                                                             @NotNull String[] proguardConfigFileOsPaths,
                                                                             boolean includeSystemProguardFile,
                                                                             @NotNull String inputJarOsPath,
                                                                             @NotNull String[] externalJarOsPaths,
                                                                             @NotNull String outputJarFileOsPath,
                                                                             @Nullable String logDirOutputOsPath) throws IOException {
    final List<String> commands = new ArrayList<String>();
    final String toolOsPath = sdkOsPath + File.separator + SdkConstants.OS_SDK_TOOLS_PROGUARD_BIN_FOLDER + SdkConstants.FN_PROGUARD;
    commands.add(toolOsPath);

    final String proguardHome = sdkOsPath + File.separator + SdkConstants.FD_TOOLS + File.separator + SdkConstants.FD_PROGUARD;

    final String systemProguardCfgPath = proguardHome + File.separator + SYSTEM_PROGUARD_CFG_FILE_NAME;

    if (isIncludingInProguardSupported(sdkToolsRevision)) {
      if (includeSystemProguardFile) {
        commands.add("-include");
        commands.add(quotePath(systemProguardCfgPath));
      }
      for (String proguardConfigFileOsPath : proguardConfigFileOsPaths) {
        commands.add("-include");
        commands.add(quotePath(proguardConfigFileOsPath));
      }
    }
    else {
      commands.add("@" + quotePath(proguardConfigFileOsPaths[0]));
    }

    commands.add("-injars");

    StringBuilder builder = new StringBuilder(quotePath(inputJarOsPath));

    for (String jarFile : externalJarOsPaths) {
      builder.append(File.pathSeparatorChar);
      builder.append(quotePath(jarFile));
    }
    commands.add(builder.toString());

    commands.add("-outjars");
    commands.add(quotePath(outputJarFileOsPath));

    commands.add("-libraryjars");

    builder = new StringBuilder(quotePath(target.getPath(IAndroidTarget.ANDROID_JAR)));

    IAndroidTarget.IOptionalLibrary[] libraries = target.getOptionalLibraries();
    if (libraries != null) {
      for (IAndroidTarget.IOptionalLibrary lib : libraries) {
        builder.append(File.pathSeparatorChar);
        builder.append(quotePath(lib.getJarPath()));
      }
    }
    commands.add(builder.toString());

    if (logDirOutputOsPath != null) {
      commands.add("-dump");
      commands.add(quotePath(new File(logDirOutputOsPath, "dump.txt").getAbsolutePath()));

      commands.add("-printseeds");
      commands.add(quotePath(new File(logDirOutputOsPath, "seeds.txt").getAbsolutePath()));

      commands.add("-printusage");
      commands.add(quotePath(new File(logDirOutputOsPath, "usage.txt").getAbsolutePath()));

      commands.add("-printmapping");
      commands.add(quotePath(new File(logDirOutputOsPath, "mapping.txt").getAbsolutePath()));
    }

    LOG.info(command2string(commands));
    final Map<String, String> home = System.getenv().containsKey(PROGUARD_HOME_ENV_VARIABLE)
                                     ? Collections.<String, String>emptyMap()
                                     : Collections.singletonMap(PROGUARD_HOME_ENV_VARIABLE, proguardHome);
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(commands), home);
  }

  private static String quotePath(String path) {
    if (path.indexOf(' ') != -1) {
      path = '\'' + path + '\'';
    }
    return path;
  }

  public static String buildTempInputJar(@NotNull String[] classFilesDirOsPaths, @NotNull String[] libClassFilesDirOsPaths)
    throws IOException {
    final File inputJar = FileUtil.createTempFile("proguard_input", ".jar");

    packClassFilesIntoJar(classFilesDirOsPaths, libClassFilesDirOsPaths, inputJar);

    return FileUtil.toSystemDependentName(inputJar.getPath());
  }

  public static String toolPath(@NotNull String toolFileName) {
    return SdkConstants.OS_SDK_TOOLS_FOLDER + toolFileName;
  }

  public static boolean isIncludingInProguardSupported(int sdkToolsRevision) {
    return sdkToolsRevision == -1 || sdkToolsRevision >= 17;
  }

  public static int parsePackageRevision(@NotNull String sdkDirOsPath, @NotNull String packageDirName) {
    final File propFile =
      new File(sdkDirOsPath + File.separatorChar + packageDirName + File.separatorChar + SdkConstants.FN_SOURCE_PROP);
    int revisionNumber = -1;
    if (propFile.exists() && propFile.isFile()) {
      final Map<String, String> map =
        ProjectProperties.parsePropertyFile(new BufferingFileWrapper(propFile), new MessageBuildingSdkLog());
      String revision = map.get("Pkg.Revision");

      if (revision != null) {
        final int dot = revision.indexOf('.');
        if (dot > 0) {
          revision = revision.substring(0, dot);
        }

        try {
          revisionNumber = Integer.parseInt(revision);
        }
        catch (NumberFormatException e) {
          LOG.debug(e);
        }
      }
    }
    return revisionNumber > 0 ? revisionNumber : -1;
  }

  @NotNull
  public static String readFile(@NotNull File file) throws IOException {
    final InputStream is = new BufferedInputStream(new FileInputStream(file));
    try {
      final byte[] data = new byte[is.available()];
      //noinspection ResultOfMethodCallIgnored
      is.read(data);
      return new String(data);
    }
    finally {
      is.close();
    }
  }

  public static boolean contains2Identifiers(String packageName) {
    return packageName.split("\\.").length >= 2;
  }

  public static boolean directoriesContainSameContent(@NotNull File dir1, @NotNull File dir2, @Nullable FileFilter filter)
    throws IOException {
    if (dir1.exists() != dir2.exists()) {
      return false;
    }

    final File[] children1 = getFilteredChildren(dir1, filter);
    final File[] children2 = getFilteredChildren(dir2, filter);

    if (children1 == null || children2 == null) {
      return children1 == children2;
    }

    if (children1.length != children2.length) {
      return false;
    }

    for (int i = 0; i < children1.length; i++) {
      final File child1 = children1[i];
      final File child2 = children2[i];

      if (!Comparing.equal(child1.getName(), child2.getName())) {
        return false;
      }

      final boolean childDir = child1.isDirectory();
      if (childDir != child2.isDirectory()) {
        return false;
      }

      if (childDir) {
        if (!directoriesContainSameContent(child1, child2, filter)) {
          return false;
        }
      }
      else {
        final String content1 = readFile(child1);
        final String content2 = readFile(child2);

        if (!Comparing.equal(content1, content2)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  private static File[] getFilteredChildren(@NotNull File dir, @Nullable FileFilter filter) {
    final File[] children = dir.listFiles();
    if (children == null || children.length == 0 || filter == null) {
      return children;
    }

    final List<File> result = new ArrayList<File>();
    for (File child : children) {
      if (child.isDirectory() || filter.accept(child)) {
        result.add(child);
      }
    }
    return result.toArray(new File[result.size()]);
  }

  @NotNull
  public static String addSuffixToFileName(@NotNull String path, @NotNull String suffix) {
    final int dot = path.lastIndexOf('.');
    if (dot < 0) {
      return path + suffix;
    }
    final String a = path.substring(0, dot);
    final String b = path.substring(dot);
    return a + suffix + b;
  }

  public static void signApk(@NotNull File srcApk,
                             @NotNull File destFile,
                             @NotNull PrivateKey privateKey,
                             @NotNull X509Certificate certificate)
    throws IOException, GeneralSecurityException {
    FileOutputStream fos = new FileOutputStream(destFile);
    SignedJarBuilder builder = new SafeSignedJarBuilder(fos, privateKey, certificate, destFile.getPath());
    FileInputStream fis = new FileInputStream(srcApk);
    try {
      builder.writeZip(fis, null);
      builder.close();
    }
    finally {
      try {
        fis.close();
      }
      catch (IOException ignored) {
      }
      finally {
        try {
          fos.close();
        }
        catch (IOException ignored) {
        }
      }
    }
  }

  @NotNull
  public static String getStackTrace(@NotNull Throwable t) {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter);
    try {
      t.printStackTrace(writer);
      return stringWriter.toString();
    }
    finally {
      writer.close();
    }
  }

  public static boolean hasXmxParam(@NotNull List<String> parameters) {
    for (String param : parameters) {
      if (param.startsWith("-Xmx")) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String executeZipAlign(@NotNull String zipAlignPath, @NotNull File source, @NotNull File destination) {
    final ProcessBuilder processBuilder = new ProcessBuilder(
      zipAlignPath, "-f", "4", source.getAbsolutePath(), destination.getAbsolutePath());

    BaseOSProcessHandler handler;
    try {
      handler = new BaseOSProcessHandler(processBuilder.start(), "", null);
    }
    catch (IOException e) {
      return e.getMessage();
    }
    final StringBuilder builder = new StringBuilder();
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        builder.append(event.getText());
      }
    });
    handler.startNotify();
    handler.waitFor();
    int exitCode = handler.getProcess().exitValue();
    return exitCode != 0 ? builder.toString() : null;
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> buildArtifact(@NotNull String artifactName,
                                                                            @NotNull String messagePrefix,
                                                                            @NotNull String sdkLocation,
                                                                            @Nullable String artifactFilePath,
                                                                            @NotNull String keyStorePath,
                                                                            @Nullable String keyAlias,
                                                                            @Nullable String keyStorePassword,
                                                                            @Nullable String keyPassword)
    throws GeneralSecurityException, IOException {
    final Map<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>();
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    final Pair<PrivateKey, X509Certificate> pair = getPrivateKeyAndCertificate(messagePrefix, messages, keyAlias, keyStorePath,
                                                                               keyStorePassword, keyPassword);
    if (pair == null) {
      return messages;
    }
    final String prefix = "Cannot sign artifact " + artifactName + ": ";

    if (artifactFilePath == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(prefix + "output path is not specified");
      return messages;
    }

    final File artifactFile = new File(artifactFilePath);
    if (!artifactFile.exists()) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(prefix + "file " + artifactFilePath + " hasn't been generated");
      return messages;
    }
    final String zipAlignPath =
      FileUtil.toSystemDependentName(sdkLocation + '/' + toolPath(SdkConstants.FN_ZIPALIGN));
    final boolean runZipAlign = new File(zipAlignPath).isFile();

    File tmpDir = null;
    try {
      tmpDir = FileUtil.createTempDirectory("android_artifact", "tmp");
      final File tmpArtifact = new File(tmpDir, "tmpArtifact.apk");

      if (runZipAlign) {
        final String errorMessage = executeZipAlign(zipAlignPath, artifactFile, tmpArtifact);
        if (errorMessage != null) {
          messages.get(AndroidCompilerMessageKind.ERROR).add(messagePrefix + "zip-align: " + errorMessage);
          return messages;
        }
      }
      else {
        messages.get(AndroidCompilerMessageKind.WARNING).add(messagePrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.zip.align.error"));
        FileUtil.copy(artifactFile, tmpArtifact);
      }

      if (!FileUtil.delete(artifactFile)) {
        messages.get(AndroidCompilerMessageKind.ERROR).add("Cannot delete file " + artifactFile.getPath());
        return messages;
      }
      signApk(tmpArtifact, artifactFile, pair.getFirst(), pair.getSecond());
    }
    finally {
      if (tmpDir != null) {
        FileUtil.delete(tmpDir);
      }
    }
    return messages;
  }

  @Nullable
  private static Pair<PrivateKey, X509Certificate> getPrivateKeyAndCertificate(@NotNull String errorPrefix,
                                                                               @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                                                               @Nullable String keyAlias,
                                                                               @Nullable String keyStoreFilePath,
                                                                               @Nullable String keyStorePasswordStr,
                                                                               @Nullable String keyPasswordStr)
    throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException, UnrecoverableEntryException {

    if (keyStoreFilePath == null || keyStoreFilePath.length() == 0) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key store file is not specified");
      return null;
    }
    if (keyStorePasswordStr == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key store password is not specified");
      return null;
    }
    if (keyAlias == null || keyAlias.length() == 0) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key alias is not specified");
      return null;
    }
    if (keyPasswordStr == null) {
      messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + "Key password is not specified");
      return null;
    }
    final File keyStoreFile = new File(keyStoreFilePath);
    final char[] keystorePassword = keyStorePasswordStr.toCharArray();
    final char[] plainKeyPassword = keyPasswordStr.toCharArray();

    final KeyStore keyStore;
    InputStream is = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      is = new FileInputStream(keyStoreFile);
      keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(is, keystorePassword);

      final KeyStore.PrivateKeyEntry entry =
        (KeyStore.PrivateKeyEntry)keyStore.getEntry(keyAlias, new KeyStore.PasswordProtection(plainKeyPassword));
      if (entry == null) {
        messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.key.error", keyAlias));
        return null;
      }

      final PrivateKey privateKey = entry.getPrivateKey();
      final Certificate certificate = entry.getCertificate();
      if (privateKey == null || certificate == null) {
        messages.get(AndroidCompilerMessageKind.ERROR).add(errorPrefix + AndroidCommonBundle.message(
          "android.artifact.building.cannot.find.key.error", keyAlias));
        return null;
      }
      return Pair.create(privateKey, (X509Certificate)certificate);
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
  }
}
