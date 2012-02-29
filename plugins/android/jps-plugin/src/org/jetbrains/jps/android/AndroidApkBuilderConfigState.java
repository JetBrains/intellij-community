package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.ValidityState;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApkBuilderConfigState implements ValidityState {
  private final String myOutputApkPath;
  private final String myCustomKeystorePath;

  public AndroidApkBuilderConfigState(@NotNull String outputApkPath, @NotNull String customKeystorePath) {
    myOutputApkPath = outputApkPath;
    myCustomKeystorePath = customKeystorePath;
  }

  public AndroidApkBuilderConfigState(DataInput in) throws IOException {
    myOutputApkPath = in.readUTF();
    myCustomKeystorePath = in.readUTF();
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidApkBuilderConfigState)) {
      return false;
    }
    final AndroidApkBuilderConfigState apkBuilderConfigState = (AndroidApkBuilderConfigState)otherState;
    return apkBuilderConfigState.myOutputApkPath.equals(myOutputApkPath) &&
           apkBuilderConfigState.myCustomKeystorePath.equals(myCustomKeystorePath);
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeUTF(myOutputApkPath);
    out.writeUTF(myCustomKeystorePath);
  }
}
