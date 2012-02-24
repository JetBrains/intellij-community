package org.jetbrains.android.util;

import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
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
  private static Pattern R_PATTERN = Pattern.compile("R(\\$.*)?\\.class");

  public static final Pattern COMPILER_MESSAGE_PATTERN = Pattern.compile("(.+):(\\d+):.+");
  public static final String[] FILE_RESOURCE_TYPES = new String[]{"drawable", "anim", "layout", "values", "menu", "xml", "raw", "color"};
  @NonNls public static final String PNG_EXTENSION = "png";
  private static final String[] DRAWABLE_EXTENSIONS = new String[]{PNG_EXTENSION, "jpg", "gif"};

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

          LOG.info(msg);
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
                                   boolean packRClasses,
                                   @NotNull List<Pair<File, String>> files)
    throws IOException {

    if (file.isDirectory()) {
      final File[] children = file.listFiles();

      if (children != null) {
        for (File child : children) {
          addFileToJar(child, rootDirectory, packRClasses, files);
        }
      }
    }
    else if (file.isFile()) {
      if (!FileUtil.getExtension(file.getName()).equals("class")) {
        return;
      }

      if (!packRClasses && R_PATTERN.matcher(file.getName()).matches()) {
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
    return ArrayUtil.find(FILE_RESOURCE_TYPES, type) >= 0 ? type : null;
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
}
