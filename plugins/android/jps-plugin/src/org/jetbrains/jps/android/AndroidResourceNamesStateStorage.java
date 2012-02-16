package org.jetbrains.jps.android;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.Paths;
import org.jetbrains.jps.incremental.storage.AbstractStateStorage;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourceNamesStateStorage extends AbstractStateStorage<String, AndroidResourceNamesValidityState> {

  @NonNls private static final String ANDROID_STORAGE_DIR = "android_resource_names";
  @NonNls private static final String RESOURCE_NAMES_STORAGE = "resource_names";

  public AndroidResourceNamesStateStorage(@NotNull String projectName) throws IOException {
    super(getStorageFile(projectName), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  @NotNull
  private static File getStorageFile(String projectName) {
    return new File(new File(Paths.getDataStorageRoot(projectName), ANDROID_STORAGE_DIR), RESOURCE_NAMES_STORAGE);
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidResourceNamesValidityState> {

    @Override
    public void save(DataOutput out, AndroidResourceNamesValidityState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidResourceNamesValidityState read(DataInput in) throws IOException {
      return new AndroidResourceNamesValidityState(in);
    }
  }
}
