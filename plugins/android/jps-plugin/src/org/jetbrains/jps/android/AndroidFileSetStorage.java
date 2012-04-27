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
public class AndroidFileSetStorage extends AbstractStateStorage<String, AndroidFileSetState> {

  public AndroidFileSetStorage(@NotNull File dataStorageRoot, @NotNull String storageName) throws IOException {
    super(AndroidJpsUtil.getStorageFile(dataStorageRoot, storageName), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidFileSetState> {

    @Override
    public void save(DataOutput out, AndroidFileSetState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidFileSetState read(DataInput in) throws IOException {
      return new AndroidFileSetState(in);
    }
  }
}
