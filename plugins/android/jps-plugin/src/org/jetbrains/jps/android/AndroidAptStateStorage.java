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
public class AndroidAptStateStorage extends AbstractStateStorage<String, AndroidAptValidityState> {

  public static final StorageProvider<AndroidAptStateStorage> PROVIDER = new StorageProvider<AndroidAptStateStorage>() {
    @NotNull
    @Override
    public AndroidAptStateStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidAptStateStorage(targetDataDir);
    }
  };

  @NonNls private static final String RESOURCE_NAMES_STORAGE = "resource_names";

  private AndroidAptStateStorage(@NotNull File dataStorageRoot) throws IOException {
    super(AndroidJpsUtil.getStorageFile(dataStorageRoot, RESOURCE_NAMES_STORAGE), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidAptValidityState> {

    @Override
    public void save(DataOutput out, AndroidAptValidityState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidAptValidityState read(DataInput in) throws IOException {
      return new AndroidAptValidityState(in);
    }
  }
}
