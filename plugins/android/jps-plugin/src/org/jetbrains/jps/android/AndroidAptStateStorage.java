package org.jetbrains.jps.android;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidAptStateStorage extends AbstractStateStorage<String, AndroidAptValidityState> {

  @NonNls private static final String RESOURCE_NAMES_STORAGE = "resource_names";

  public AndroidAptStateStorage(@NotNull File dataStorageRoot) throws IOException {
    super(getStorageFile(dataStorageRoot), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  @NotNull
  private static File getStorageFile(@NotNull File dataStorageRoot) {
    return new File(new File(new File(dataStorageRoot, AndroidJpsUtil.ANDROID_STORAGE_DIR), RESOURCE_NAMES_STORAGE),
                    RESOURCE_NAMES_STORAGE);
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
