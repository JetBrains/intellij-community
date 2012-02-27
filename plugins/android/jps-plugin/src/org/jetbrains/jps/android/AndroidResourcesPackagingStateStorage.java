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
public class AndroidResourcesPackagingStateStorage extends AbstractStateStorage<String, AndroidResourcesPackagingState> {
  @NonNls private static final String RELEASE_SUFFIX = "_release";
  @NonNls private static final String RESOURCES_AND_ASSETS_STORAGE = "resources_and_assets";

  public AndroidResourcesPackagingStateStorage(@NotNull File dataStorageRoot, boolean release) throws IOException {
    super(getStorageFile(dataStorageRoot, release), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  @NotNull
  private static File getStorageFile(@NotNull File dataStorageRoot, boolean release) {
    return new File(new File(new File(dataStorageRoot, AndroidJpsUtil.ANDROID_STORAGE_DIR), getStorageDirName(release)),
                    RESOURCES_AND_ASSETS_STORAGE);
  }

  @NotNull
  private static String getStorageDirName(boolean release) {
    return release ? RESOURCES_AND_ASSETS_STORAGE + RELEASE_SUFFIX : RESOURCES_AND_ASSETS_STORAGE;
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidResourcesPackagingState> {

    @Override
    public void save(DataOutput out, AndroidResourcesPackagingState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidResourcesPackagingState read(DataInput in) throws IOException {
      return new AndroidResourcesPackagingState(in);
    }
  }
}
