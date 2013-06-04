package org.jetbrains.jps.android;

import com.android.sdklib.SdkManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ValueResourcesFileParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildDataCache {
  private static AndroidBuildDataCache ourInstance;

  private final Map<String, MyComputedValue<SdkManager>> mySdkManagers =
    new HashMap<String, MyComputedValue<SdkManager>>();
  private final Map<JpsModule, MyAndroidDeps> myModule2AndroidDeps = new HashMap<JpsModule, MyAndroidDeps>();
  private final Map<String, List<ResourceEntry>> myParsedValueResourceFiles = new HashMap<String, List<ResourceEntry>>();

  @NotNull
  public static AndroidBuildDataCache getInstance() {
    if (ourInstance == null) {
      ourInstance = new AndroidBuildDataCache();
    }
    return ourInstance;
  }

  public static void clean() {
    ourInstance = null;
  }

  // If parsing throws IOException, the result it is not cached, so invoker should catch it and stop the build
  public List<ResourceEntry> getParsedValueResourceFile(@NotNull File file) throws IOException {
    final String path = FileUtil.toCanonicalPath(file.getPath());
    List<ResourceEntry> entries = myParsedValueResourceFiles.get(path);

    if (entries == null) {
      entries = parseValueResourceFile(file);
      myParsedValueResourceFiles.put(path, entries);
    }
    return entries;
  }

  @NotNull
  private static List<ResourceEntry> parseValueResourceFile(@NotNull File valueResXmlFile)
    throws IOException {
    final ArrayList<ResourceEntry> result = new ArrayList<ResourceEntry>();

    final InputStream inputStream = new BufferedInputStream(new FileInputStream(valueResXmlFile));
    try {
      FormsParsing.parse(inputStream, new ValueResourcesFileParser() {
        @Override
        protected void stop() {
          throw new FormsParsing.ParserStoppedException();
        }

        @Override
        protected void process(@NotNull ResourceEntry resourceEntry) {
          result.add(resourceEntry);
        }
      });
    }
    finally {
      inputStream.close();
    }
    return result;
  }

  @NotNull
  public List<JpsAndroidModuleExtension> getAllAndroidDependencies(@NotNull JpsModule module, boolean librariesOnly) {
    MyAndroidDeps deps = myModule2AndroidDeps.get(module);

    if (deps == null) {
      deps = computeAndroidDependencies(module);
      myModule2AndroidDeps.put(module, deps);
    }
    return librariesOnly ? deps.myLibAndroidDeps : deps.myAndroidDeps;
  }

  @NotNull
  private static MyAndroidDeps computeAndroidDependencies(@NotNull JpsModule module) {
    final MyAndroidDeps result = new MyAndroidDeps();
    final boolean recursively = AndroidJpsUtil.shouldProcessDependenciesRecursively(module);
    collectAndroidDependencies(module, result, new HashSet<String>(), true, recursively);
    Collections.reverse(result.myAndroidDeps);
    Collections.reverse(result.myLibAndroidDeps);
    return result;
  }

  private static void collectAndroidDependencies(@NotNull JpsModule module,
                                                 @NotNull MyAndroidDeps result,
                                                 @NotNull Set<String> visitedSet,
                                                 boolean fillLibs,
                                                 boolean recursively) {
    final List<JpsDependencyElement> dependencies =
      new ArrayList<JpsDependencyElement>(JpsJavaExtensionService.getInstance().getDependencies(
        module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false));

    for (int i = dependencies.size() - 1; i >= 0; i--) {
      final JpsDependencyElement item = dependencies.get(i);

      if (item instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)item).getModule();
        if (depModule != null) {
          final JpsAndroidModuleExtension depExtension = AndroidJpsUtil.getExtension(depModule);
          if (depExtension != null && visitedSet.add(depModule.getName())) {

            if (recursively) {
              final boolean newRecursively = AndroidJpsUtil.shouldProcessDependenciesRecursively(depModule);
              collectAndroidDependencies(depModule, result, visitedSet, fillLibs && depExtension.isLibrary(), newRecursively);
            }
            result.myAndroidDeps.add(depExtension);

            if (fillLibs && depExtension.isLibrary()) {
              result.myLibAndroidDeps.add(depExtension);
            }
          }
        }
      }
    }
  }

  @NotNull
  public SdkManager getSdkManager(@NotNull String androidSdkHomePath)
    throws ComputationException {
    MyComputedValue<SdkManager> value = mySdkManagers.get(FileUtil.toCanonicalPath(androidSdkHomePath));

    if (value == null) {
      value = computeSdkManager(androidSdkHomePath);
      mySdkManagers.put(androidSdkHomePath, value);
    }
    return value.getValue();
  }

  @NotNull
  private static MyComputedValue<SdkManager> computeSdkManager(@NotNull String androidSdkHomePath) {
    final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    final SdkManager manager = AndroidCommonUtils.createSdkManager(androidSdkHomePath, log);

    if (manager == null) {
      final String message = log.getErrorMessage();
      return new ErrorComputedValue<SdkManager>("Android SDK is parsed incorrectly." + (message.length() > 0 ? " Parsing log:\n" + message : ""));
    }
    return new SuccessComputedValue<SdkManager>(manager);
  }

  private abstract static class MyComputedValue<T> {
    @NotNull
    abstract T getValue() throws ComputationException;
  }

  private static class SuccessComputedValue<T> extends MyComputedValue<T> {
    @NotNull
    private final T myValue;

    private SuccessComputedValue(@NotNull T value) {
      myValue = value;
    }

    @NotNull
    @Override
    T getValue() throws ComputationException {
      return myValue;
    }
  }

  private static class ErrorComputedValue<T> extends MyComputedValue<T> {
    @NotNull
    private final String myMessage;

    private ErrorComputedValue(@NotNull String message) {
      myMessage = message;
    }

    @NotNull
    @Override
    T getValue() throws ComputationException {
      throw new ComputationException(myMessage);
    }
  }

  public static class ComputationException extends Exception {
    public ComputationException(@NotNull String message) {
      super(message);
    }
  }

  private static class MyAndroidDeps {
    final List<JpsAndroidModuleExtension> myAndroidDeps = new ArrayList<JpsAndroidModuleExtension>();
    final List<JpsAndroidModuleExtension> myLibAndroidDeps = new ArrayList<JpsAndroidModuleExtension>();
  }
}
