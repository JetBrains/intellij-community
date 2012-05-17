package org.jetbrains.jps.android;

import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApkBuilderConfigState implements ValidityState {
  private final String myOutputApkPath;
  private final String myCustomKeystorePath;
  private final List<AndroidNativeLibData> myAdditionalNativeLibs;

  public AndroidApkBuilderConfigState(@NotNull String outputApkPath,
                                      @NotNull String customKeystorePath,
                                      @NotNull List<AndroidNativeLibData> additionalNativeLibs) {
    myOutputApkPath = outputApkPath;
    myCustomKeystorePath = customKeystorePath;
    myAdditionalNativeLibs = additionalNativeLibs;
  }

  public AndroidApkBuilderConfigState(DataInput in) throws IOException {
    myOutputApkPath = in.readUTF();
    myCustomKeystorePath = in.readUTF();

    final int additionalNativeLibsCount = in.readInt();
    myAdditionalNativeLibs = new ArrayList<AndroidNativeLibData>(additionalNativeLibsCount);
    for (int i = 0; i < additionalNativeLibsCount; i++) {
      final String architecture = in.readUTF();
      final String path = in.readUTF();
      final String targetFileName = in.readUTF();
      myAdditionalNativeLibs.add(new AndroidNativeLibData(architecture, path, targetFileName));
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidApkBuilderConfigState)) {
      return false;
    }
    final AndroidApkBuilderConfigState apkBuilderConfigState = (AndroidApkBuilderConfigState)otherState;
    return apkBuilderConfigState.myOutputApkPath.equals(myOutputApkPath) &&
           apkBuilderConfigState.myCustomKeystorePath.equals(myCustomKeystorePath) &&
           apkBuilderConfigState.myAdditionalNativeLibs.equals(myAdditionalNativeLibs);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myOutputApkPath);
    out.writeUTF(myCustomKeystorePath);

    out.writeInt(myAdditionalNativeLibs.size());
    for (AndroidNativeLibData lib : myAdditionalNativeLibs) {
      out.writeUTF(lib.getArchitecture());
      out.writeUTF(lib.getPath());
      out.writeUTF(lib.getTargetFileName());
    }
  }
}
