package org.jetbrains.android.util;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.utils.ILogger;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidCommonUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidCommonUtils");

  @NonNls public static final Object MANIFEST_JAVA_FILE_NAME = "Manifest.java";
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
  @NonNls public static final String LIGHT_BUILD_OPTION = "LIGHT_BUILD_KEY";
  @NonNls public static final String PROGUARD_CFG_PATH_OPTION = "ANDROID_PROGUARD_CFG_PATH";
  @NonNls public static final String DIRECTORY_FOR_LOGS_NAME = "proguard_logs";
  @NonNls public static final String PROGUARD_OUTPUT_JAR_NAME = "obfuscated_sources.jar";
  @NonNls public static final String INCLUDE_SYSTEM_PROGUARD_FILE_OPTION = "INCLUDE_SYSTEM_PROGUARD_FILE";
  @NonNls public static final String SYSTEM_PROGUARD_CFG_FILE_NAME = "proguard-android.txt";
  @NonNls private static final String PROGUARD_HOME_ENV_VARIABLE = "PROGUARD_HOME";

  @NonNls public static final String ADDITIONAL_NATIVE_LIBS_ELEMENT = "additionalNativeLibs";
  @NonNls public static final String ITEM_ELEMENT = "item";
  @NonNls public static final String ARCHITECTURE_ATTRIBUTE = "architecture";
  @NonNls public static final String URL_ATTRIBUTE = "url";
  @NonNls public static final String TARGET_FILE_NAME_ATTRIBUTE = "targetFileName";

  private AndroidCommonUtils() {
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
  public static SdkManager createSdkManager(@NotNull String path, @NotNull ILogger log) {
    path = FileUtil.toSystemDependentName(path);

    final File f = new File(path);
    if (!f.exists() || !f.isDirectory()) {
      return null;
    }

    final File platformsDir = new File(f, SdkConstants.FD_PLATFORMS);
    if (!platformsDir.exists() || !platformsDir.isDirectory()) {
      return null;
    }

    return SdkManager.createManager(path + File.separatorChar, log);
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
        addFileToJar(firstPackageDir, firstPackageDir.getParentFile(), true, files);
      }
    }

    for (String path : libFirstPackageDirPaths) {
      final File firstPackageDir = new File(path);
      if (firstPackageDir.exists()) {
        addFileToJar(firstPackageDir, firstPackageDir.getParentFile(), false, files);
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

  private static void addFileToJar(@NotNull File file,
                                   @NotNull File rootDirectory,
                                   boolean packRAndManifestClasses,
                                   @NotNull List<Pair<File, String>> files)
    throws IOException {

    if (file.isDirectory()) {
      final File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          addFileToJar(child, rootDirectory, packRAndManifestClasses, files);
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
}
