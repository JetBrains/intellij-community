package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProguardCompiler implements ClassPostProcessingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidProguardCompiler");
  @NonNls private static final String DIRECTORY_FOR_LOGS_NAME = "proguard_logs";
  @NonNls static final String PROGUARD_OUTPUT_JAR_NAME = "obfuscated_sources.jar";

  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<ProcessingItem[]>() {
      @Override
      public ProcessingItem[] compute() {
        final Module[] modules = ModuleManager.getInstance(context.getProject()).getModules();
        final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
        
        for (final Module module : modules) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);

          if (facet == null || facet.getConfiguration().LIBRARY_PROJECT) {
            continue;
          }

          if (!AndroidCompileUtil.isReleaseBuild(context)) {
            continue;
          }

          final VirtualFile proguardConfigFile = AndroidCompileUtil.getProguardConfigFile(facet);
          if (proguardConfigFile == null) {
            continue;
          }

          final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
          if (extension == null) {
            LOG.error("Cannot find compiler module extension for module " + module.getName());
            continue;
          }

          final VirtualFile classFilesDir = extension.getCompilerOutputPath();
          if (classFilesDir == null) {
            context
              .addMessage(CompilerMessageCategory.INFORMATION, "Output directory is not specified for module " + module.getName(), null,
                          -1, -1);
            continue;
          }

          final VirtualFile mainContentRoot = AndroidRootUtil.getMainContentRoot(facet);
          if (mainContentRoot == null) {
            context.addMessage(CompilerMessageCategory.ERROR, "Cannot find main content root for module " + module.getName(), null, -1, -1);
            continue;
          }

          final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
          if (platform == null) {
            context.addMessage(CompilerMessageCategory.ERROR,
                               AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          final String logsDirOsPath = FileUtil.toSystemDependentName(mainContentRoot.getPath() + '/' + DIRECTORY_FOR_LOGS_NAME);

          final File logsDir = new File(logsDirOsPath);
          if (!logsDir.exists()) {
            if (!logsDir.mkdirs()) {
              context.addMessage(CompilerMessageCategory.ERROR, "Cannot find directory " + logsDirOsPath, null, -1, -1);
              continue;
            }
          }

          final List<VirtualFile> externalJars = AndroidRootUtil.getExternalLibraries(module);

          final Set<VirtualFile> classFilesDirs = new HashSet<VirtualFile>();
          AndroidDexCompiler.addModuleOutputDir(classFilesDirs, classFilesDir);
          
          for (VirtualFile file : AndroidRootUtil.getDependentModules(module, classFilesDir)) {
            AndroidDexCompiler.addModuleOutputDir(classFilesDirs, file);
          }

          final String sdkPath = FileUtil.toSystemDependentName(platform.getSdk().getLocation());

          final VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
          final String outputJarOsPath = FileUtil.toSystemDependentName(outputDir.getPath() + '/' + PROGUARD_OUTPUT_JAR_NAME);

          items.add(new MyProcessingItem(module, sdkPath, platform.getTarget(), proguardConfigFile, outputJarOsPath, classFilesDir,
                                         classFilesDirs.toArray(new VirtualFile[classFilesDirs.size()]),
                                         externalJars.toArray(new VirtualFile[externalJars.size()]), logsDirOsPath));
        }
        return items.toArray(new ProcessingItem[items.size()]);
      }
    });
  }

  @Override
  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    final List<ProcessingItem> processedItems = new ArrayList<ProcessingItem>();
    
    for (ProcessingItem item : items) {
      final MyProcessingItem processingItem = (MyProcessingItem)item;
      
      if (!AndroidCompileUtil.isModuleAffected(context, processingItem.myModule)) {
        continue;
      }

      final String proguardConfigFileOsPath = FileUtil.toSystemDependentName(processingItem.getProguardConfigFile().getPath());

      final String[] classFilesDirOsPaths = toOsPaths(processingItem.getAllClassFilesDirs());
      final String[] externalJarOsPaths = toOsPaths(processingItem.getExternalJars());
 
      try {
        final String inputJarOsPath = buildTempInputJar(classFilesDirOsPaths);
        final String logsDirOsPath = processingItem.getLogsDirectoryOsPath();

        final Map<CompilerMessageCategory, List<String>> messages =
          launchProguard(processingItem.getTarget(), processingItem.getSdkOsPath(), proguardConfigFileOsPath, inputJarOsPath,
                         externalJarOsPaths, processingItem.getOutputJarOsPath(), logsDirOsPath);

        CompilerUtil.refreshIOFile(new File(processingItem.getOutputJarOsPath()));

        AndroidCompileUtil.addMessages(context, messages);
        
        if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
          processedItems.add(item);
        }
      }
      catch (IOException e) {
        if (e.getMessage() == null) {
          LOG.error(e);
        }
        else {
          LOG.info(e);
          context.addMessage(CompilerMessageCategory.ERROR, "I/O error: " + e.getMessage(), null, -1, -1);
        }
      }
    }
    
    return processedItems.toArray(new ProcessingItem[processedItems.size()]);
  }

  @NotNull
  private static String[] toOsPaths(@NotNull VirtualFile[] classFilesDirs) {
    final String[] classFilesDirOsPaths = new String[classFilesDirs.length];

    for (int i = 0; i < classFilesDirs.length; i++) {
      classFilesDirOsPaths[i] = FileUtil.toSystemDependentName(classFilesDirs[i].getPath());
    }
    return classFilesDirOsPaths;
  }

  private static String buildTempInputJar(@NotNull String[] classFilesDirOsPaths) throws IOException {
    final File inputJar = FileUtil.createTempFile("proguard_input", ".jar");
    
    final JarOutputStream jos = new JarOutputStream(new FileOutputStream(inputJar));
    try {
      for (String path : classFilesDirOsPaths) {
        final File firstPackageDir = new File(path);
        if (firstPackageDir.exists()) {
          addFileToJar(jos, firstPackageDir, firstPackageDir.getParentFile());
        }
      }
    }
    finally {
      jos.close();
    }
    
    return FileUtil.toSystemDependentName(inputJar.getPath());
  }

  @NotNull
  private static Map<CompilerMessageCategory, List<String>> launchProguard(@NotNull IAndroidTarget target,
                                                                           @NotNull String sdkOsPath,
                                                                           @NotNull String proguardConfigFileOsPath,
                                                                           @NotNull String inputJarOsPath,
                                                                           @NotNull String[] externalJarOsPaths,
                                                                           @NotNull String outputJarFileOsPath,
                                                                           @Nullable String logDirOutputOsPath) throws IOException {

    final List<String> commands = new ArrayList<String>();
    final String toolOsPath = sdkOsPath + File.separator + SdkConstants.OS_SDK_TOOLS_PROGUARD_BIN_FOLDER + SdkConstants.FN_PROGUARD;

    commands.add(toolOsPath);
    commands.add("@" + quotePath(proguardConfigFileOsPath));

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
      commands.add(new File(logDirOutputOsPath, "dump.txt").getAbsolutePath());

      commands.add("-printseeds");
      commands.add(new File(logDirOutputOsPath, "seeds.txt").getAbsolutePath());

      commands.add("-printusage");
      commands.add(new File(logDirOutputOsPath, "usage.txt").getAbsolutePath());

      commands.add("-printmapping");
      commands.add(new File(logDirOutputOsPath, "mapping.txt").getAbsolutePath());
    }

    LOG.info(AndroidUtils.command2string(commands));
    return ExecutionUtil.execute(ArrayUtil.toStringArray(commands));
  }

  private static void addFileToJar(@NotNull JarOutputStream jar, @NotNull File file, @NotNull File rootDirectory)
    throws IOException {
    
    if (file.isDirectory()) {
      for (File child : file.listFiles()) {
        addFileToJar(jar, child, rootDirectory);
      }
    }
    else if (file.isFile()) {
      if (!FileUtil.getExtension(file.getName()).equals("class")) {
        return;
      }

      final String rootPath = rootDirectory.getAbsolutePath();
      
      String path = file.getAbsolutePath();
      path = FileUtil.toSystemIndependentName(path.substring(rootPath.length()));
      if (path.charAt(0) == '/') {
        path = path.substring(1);
      }

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
  }

  private static String quotePath(String path) {
    if (path.indexOf(' ') != -1) {
      path = '\'' + path + '\'';
    }
    return path;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Proguard Runner";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static class MyProcessingItem implements ProcessingItem {
    private final Module myModule;
    private final IAndroidTarget myTarget;
    private final String myOutputJarOsPath;
    private final VirtualFile myMainClassFilesDir;
    private final VirtualFile[] myAllClassFilesDirs; 
    private final VirtualFile[] myExternalJars;
    private final String myLogsDirectoryOsPath;
    private final VirtualFile myProguardConfigFile;
    private final String mySdkOsPath;

    private MyProcessingItem(@NotNull Module module,
                             @NotNull String sdkOsPath,
                             @NotNull IAndroidTarget target,
                             @NotNull VirtualFile proguardConfigFile,
                             @NotNull String outputJarOsPath,
                             @NotNull VirtualFile mainClassFilesDir,
                             @NotNull VirtualFile[] allClassFilesDirs,
                             @NotNull VirtualFile[] externalJars, 
                             @Nullable String logsDirectoryOsPath) {
      myModule = module;
      myTarget = target;
      myProguardConfigFile = proguardConfigFile;
      myOutputJarOsPath = outputJarOsPath;
      myMainClassFilesDir = mainClassFilesDir;
      mySdkOsPath = sdkOsPath;
      myAllClassFilesDirs = allClassFilesDirs;
      myExternalJars = externalJars;
      myLogsDirectoryOsPath = logsDirectoryOsPath;
    }

    @NotNull
    public String getOutputJarOsPath() {
      return myOutputJarOsPath;
    }

    @NotNull
    public String getSdkOsPath() {
      return mySdkOsPath;
    }

    @NotNull
    public VirtualFile[] getAllClassFilesDirs() {
      return myAllClassFilesDirs;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      return myMainClassFilesDir;
    }

    public VirtualFile getProguardConfigFile() {
      return myProguardConfigFile;
    }

    @NotNull
    public VirtualFile[] getExternalJars() {
      return myExternalJars;
    }

    @NotNull
    public IAndroidTarget getTarget() {
      return myTarget;
    }

    @Nullable
    public String getLogsDirectoryOsPath() {
      return myLogsDirectoryOsPath;
    }

    @Override
    public ValidityState getValidityState() {
      return new MyValidityState(myTarget, mySdkOsPath, myOutputJarOsPath, myAllClassFilesDirs, myExternalJars, 
                                 myProguardConfigFile, myLogsDirectoryOsPath);
    }

    @NotNull
    public Module getModule() {
      return myModule;
    }
  }

  private static class MyValidityState implements ValidityState {
    private final String myTargetHashString;
    private final String mySdkPath;
    private final String myOutputDirPath;
    private final String myLogsDirectoryPath;
    private final Map<String, Long> myClassFilesMap;
    private final Map<String, Long> myExternalJarsMap;
    private final long myConfigFileTimestamp;
  
    public MyValidityState(@NotNull IAndroidTarget target,
                           @NotNull String sdkOsPath,
                           @NotNull String outputJarOsPath,
                           @NotNull VirtualFile[] classFilesDirs,
                           @NotNull VirtualFile[] externalJars,
                           @NotNull VirtualFile proguardConfigFile,
                           @Nullable String logsDirectoryOsPath) {
      myTargetHashString = target.hashString();
      mySdkPath = sdkOsPath;
      myOutputDirPath = outputJarOsPath;
  
      myClassFilesMap = new HashMap<String, Long>();
      final HashSet<VirtualFile> visited = new HashSet<VirtualFile>();
      for (VirtualFile dir : classFilesDirs) {
        fillClassFilesMap(dir, visited);
      }
  
      myExternalJarsMap = new HashMap<String, Long>();
      for (VirtualFile jar : externalJars) {
        myExternalJarsMap.put(jar.getPath(), jar.getTimeStamp());
      }
      
      myConfigFileTimestamp = proguardConfigFile.getTimeStamp();
      myLogsDirectoryPath = logsDirectoryOsPath != null ? logsDirectoryOsPath : "";
    }
  
    private void fillClassFilesMap(VirtualFile file, Set<VirtualFile> visited) {
      if (file.isDirectory() && visited.add(file)) {
        for (VirtualFile child : file.getChildren()) {
          fillClassFilesMap(child, visited);
        }
      }
      else if (StdFileTypes.CLASS.equals(file.getFileType())) {
        myClassFilesMap.put(file.getPath(), file.getTimeStamp());
      }
    }
  
    public MyValidityState(@NotNull DataInput in) throws IOException {
      myTargetHashString = in.readUTF();
      
      mySdkPath = CompilerIOUtil.readString(in);
      myOutputDirPath = CompilerIOUtil.readString(in);

      myClassFilesMap = new HashMap<String, Long>();
      final int classFilesCount = in.readInt();
      
      for (int i = 0; i < classFilesCount; i++) {
        final String path = CompilerIOUtil.readString(in);
        final long timestamp = in.readLong();
        
        myClassFilesMap.put(path, timestamp);
      }
      
      myExternalJarsMap = new HashMap<String, Long>();
      final int externalJarsCount = in.readInt();
      
      for (int i = 0; i < externalJarsCount; i++) {
        final String path = CompilerIOUtil.readString(in);
        final long timestamp = in.readLong();
        
        myExternalJarsMap.put(path, timestamp);
      }
      
      myConfigFileTimestamp = in.readLong();
      myLogsDirectoryPath = CompilerIOUtil.readString(in);
    }
  
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }

      final MyValidityState other = (MyValidityState)otherState;

      return myTargetHashString.equals(other.myTargetHashString) && 
             mySdkPath.equals(other.mySdkPath) && 
             myOutputDirPath.equals(other.myOutputDirPath) && 
             myClassFilesMap.equals(other.myClassFilesMap) && 
             myExternalJarsMap.equals(other.myExternalJarsMap) && 
             myConfigFileTimestamp == other.myConfigFileTimestamp && 
             myLogsDirectoryPath.equals(other.myLogsDirectoryPath);
    }
  
    public void save(DataOutput out) throws IOException {
      out.writeUTF(myTargetHashString);
      CompilerIOUtil.writeString(mySdkPath, out);
      CompilerIOUtil.writeString(myOutputDirPath, out);
      
      out.writeInt(myClassFilesMap.size());
      for (String path : myClassFilesMap.keySet()) {
        CompilerIOUtil.writeString(path, out);
        out.writeLong(myClassFilesMap.get(path));
      }
      
      out.writeInt(myExternalJarsMap.size());
      for (String path : myExternalJarsMap.keySet()) {
        CompilerIOUtil.writeString(path, out);
        out.writeLong(myExternalJarsMap.get(path));
      }
      
      out.writeLong(myConfigFileTimestamp);
      CompilerIOUtil.writeString(myLogsDirectoryPath, out);
    }
  }
}
