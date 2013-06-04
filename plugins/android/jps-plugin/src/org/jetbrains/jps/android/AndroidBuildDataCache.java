package org.jetbrains.jps.android;

import com.android.sdklib.SdkManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildDataCache {
  private static AndroidBuildDataCache ourInstance;

  private final Map<String, MyComputedValue> mySdkManagers = new HashMap<String, MyComputedValue>();

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
}
