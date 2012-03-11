package org.jetbrains.android.compiler;

import com.android.sdklib.IAndroidTarget;
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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProguardCompiler implements ClassPostProcessingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidProguardCompiler");
  public static Key<String> PROGUARD_CFG_PATH_KEY = Key.create(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION);

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

          final String proguardCfgPath = AndroidCompileUtil.getProguardConfigFilePathIfShouldRun(facet, context);
          if (proguardCfgPath == null) {
            continue;
          }

          if (proguardCfgPath.length() == 0) {
            context.addMessage(CompilerMessageCategory.ERROR, "Proguard config file path is not specified", null, -1, -1);
            continue;
          }

          final VirtualFile proguardConfigFile =
            LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(proguardCfgPath));
          if (proguardConfigFile == null) {
            context.addMessage(CompilerMessageCategory.ERROR, "Cannot find file " + proguardCfgPath, null, -1, -1);
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

          final String logsDirOsPath = FileUtil.toSystemDependentName(mainContentRoot.getPath() + '/' + AndroidCommonUtils.DIRECTORY_FOR_LOGS_NAME);

          final File logsDir = new File(logsDirOsPath);
          if (!logsDir.exists()) {
            if (!logsDir.mkdirs()) {
              context.addMessage(CompilerMessageCategory.ERROR, "Cannot find directory " + logsDirOsPath, null, -1, -1);
              continue;
            }
          }

          final List<VirtualFile> externalJars = AndroidRootUtil.getExternalLibraries(module);
          final Set<VirtualFile> classFilesDirs = new HashSet<VirtualFile>();
          final Set<VirtualFile> libClassFilesDirs = new HashSet<VirtualFile>();

          AndroidDexCompiler.addModuleOutputDir(classFilesDirs, classFilesDir);
          
          for (VirtualFile file : AndroidRootUtil.getDependentModules(module, classFilesDir)) {
            if (file.isDirectory()) {
              AndroidDexCompiler.addModuleOutputDir(classFilesDirs, file);
            }
            else {
              AndroidDexCompiler.addModuleOutputDir(libClassFilesDirs, file.getParent());
            }
          }

          final String sdkPath = FileUtil.toSystemDependentName(platform.getSdkData().getLocation());

          final VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
          final String outputJarOsPath = FileUtil.toSystemDependentName(outputDir.getPath() + '/' + AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);

          items.add(new MyProcessingItem(module, sdkPath, platform.getTarget(), proguardConfigFile, outputJarOsPath, classFilesDir,
                                         classFilesDirs.toArray(new VirtualFile[classFilesDirs.size()]),
                                         libClassFilesDirs.toArray(new VirtualFile[libClassFilesDirs.size()]),
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

      final String[] classFilesDirOsPaths = AndroidCompileUtil.toOsPaths(processingItem.getClassFilesDirs());
      final String[] libClassFilesDirOsPaths = AndroidCompileUtil.toOsPaths(processingItem.getLibClassFilesDirs());
      final String[] externalJarOsPaths = AndroidCompileUtil.toOsPaths(processingItem.getExternalJars());
 
      try {
        final String inputJarOsPath = AndroidCommonUtils.buildTempInputJar(classFilesDirOsPaths, libClassFilesDirOsPaths);
        final String logsDirOsPath = processingItem.getLogsDirectoryOsPath();

        final Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
          AndroidCommonUtils
            .launchProguard(processingItem.getTarget(), processingItem.getSdkOsPath(), proguardConfigFileOsPath, inputJarOsPath,
                            externalJarOsPaths, processingItem.getOutputJarOsPath(), logsDirOsPath));

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
    private final VirtualFile[] myClassFilesDirs; 
    private final VirtualFile[] myLibClassFilesDirs; 
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
                             @NotNull VirtualFile[] classFilesDirs,
                             @NotNull VirtualFile[] libCLassFilesDirs,
                             @NotNull VirtualFile[] externalJars, 
                             @Nullable String logsDirectoryOsPath) {
      myModule = module;
      myTarget = target;
      myProguardConfigFile = proguardConfigFile;
      myOutputJarOsPath = outputJarOsPath;
      myMainClassFilesDir = mainClassFilesDir;
      mySdkOsPath = sdkOsPath;
      myClassFilesDirs = classFilesDirs;
      myLibClassFilesDirs = libCLassFilesDirs;
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
    public VirtualFile[] getClassFilesDirs() {
      return myClassFilesDirs;
    }

    @NotNull
    public VirtualFile[] getLibClassFilesDirs() {
      return myLibClassFilesDirs;
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
      return new MyValidityState(myTarget, mySdkOsPath, myOutputJarOsPath, myClassFilesDirs, myExternalJars, 
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
