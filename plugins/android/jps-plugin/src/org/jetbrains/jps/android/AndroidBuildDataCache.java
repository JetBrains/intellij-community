package org.jetbrains.jps.android;

import com.android.sdklib.SdkManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildDataCache {
  private static AndroidBuildDataCache ourInstance;

  private final Map<String, MyComputedValue> mySdkManagers = new HashMap<String, MyComputedValue>();
  private final Map<JpsModule, MyAndroidDeps> myModule2AndroidDeps = new HashMap<JpsModule, MyAndroidDeps>();

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
              collectAndroidDependencies(depModule, result, visitedSet, fillLibs && depExtension.isLibrary(), recursively);
            }
            result.myAndroidDeps.add(0, depExtension);

            if (fillLibs && depExtension.isLibrary()) {
              result.myLibAndroidDeps.add(0, depExtension);
            }
          }
        }
      }
    }
  }

  @NotNull
  public SdkManager getSdkManager(@NotNull String androidSdkHomePath)
    throws ComputationException {
    MyComputedValue value = mySdkManagers.get(FileUtil.toCanonicalPath(androidSdkHomePath));

    if (value == null) {
      value = computeSdkManager(androidSdkHomePath);
      mySdkManagers.put(androidSdkHomePath, value);
    }
    return (SdkManager)value.getValue();
  }

  @NotNull
  private static MyComputedValue computeSdkManager(@NotNull String androidSdkHomePath) {
    final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    final SdkManager manager = AndroidCommonUtils.createSdkManager(androidSdkHomePath, log);

    if (manager == null) {
      final String message = log.getErrorMessage();
      return new ErrorComputedValue("Android SDK is parsed incorrectly." + (message.length() > 0 ? " Parsing log:\n" + message : ""));
    }
    return new SuccessComputedValue(manager);
  }

  private abstract static class MyComputedValue {
    @NotNull
    abstract Object getValue() throws ComputationException;
  }

  private static class SuccessComputedValue extends MyComputedValue {
    @NotNull
    private final Object myValue;

    private SuccessComputedValue(@NotNull Object value) {
      myValue = value;
    }

    @NotNull
    @Override
    Object getValue() throws ComputationException {
      return myValue;
    }
  }

  private static class ErrorComputedValue extends MyComputedValue {
    @NotNull
    private final String myMessage;

    private ErrorComputedValue(@NotNull String message) {
      myMessage = message;
    }

    @NotNull
    @Override
    Object getValue() throws ComputationException {
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
