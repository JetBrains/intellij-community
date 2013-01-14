package org.jetbrains.jps.android;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildConfigStateStorage extends AbstractStateStorage<String, AndroidBuildConfigState> {

  public static final StorageProvider<AndroidBuildConfigStateStorage> PROVIDER = new StorageProvider<AndroidBuildConfigStateStorage>() {
    @NotNull
    @Override
    public AndroidBuildConfigStateStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidBuildConfigStateStorage(targetDataDir);
    }
  };

  @NonNls private static final String BUILD_CONFIG_STORAGE = "build_config";

  private AndroidBuildConfigStateStorage(@NotNull File dataStorageRoot) throws IOException {
    super(AndroidJpsUtil.getStorageFile(dataStorageRoot, BUILD_CONFIG_STORAGE), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidBuildConfigState> {

    @Override
    public void save(DataOutput out, AndroidBuildConfigState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidBuildConfigState read(DataInput in) throws IOException {
      return new AndroidBuildConfigState(in);
    }
  }
}
