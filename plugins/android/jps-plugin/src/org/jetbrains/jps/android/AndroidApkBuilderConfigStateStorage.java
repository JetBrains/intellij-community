package org.jetbrains.jps.android;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApkBuilderConfigStateStorage extends AbstractStateStorage<String, AndroidApkBuilderConfigState> {
  public AndroidApkBuilderConfigStateStorage(@NotNull File dataStorageRoot, @NotNull String storageName) throws IOException {
    super(AndroidJpsUtil.getStorageFile(dataStorageRoot, storageName), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidApkBuilderConfigState> {

    @Override
    public void save(DataOutput out, AndroidApkBuilderConfigState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidApkBuilderConfigState read(DataInput in) throws IOException {
      return new AndroidApkBuilderConfigState(in);
    }
  }
}
