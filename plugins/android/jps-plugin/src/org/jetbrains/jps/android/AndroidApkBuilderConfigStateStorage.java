package org.jetbrains.jps.android;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
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
public class AndroidApkBuilderConfigStateStorage extends AbstractStateStorage<String, AndroidApkBuilderConfigState> {
  private AndroidApkBuilderConfigStateStorage(@NotNull File dataStorageRoot, @NotNull String storageName) throws IOException {
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

  public static class Provider extends StorageProvider<AndroidApkBuilderConfigStateStorage> {

    private final String myStorageName;

    public Provider(@NotNull String storageName) {
      myStorageName = storageName;
    }

    @NotNull
    @Override
    public AndroidApkBuilderConfigStateStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidApkBuilderConfigStateStorage(targetDataDir, myStorageName);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Provider provider = (Provider)o;

      if (!myStorageName.equals(provider.myStorageName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myStorageName.hashCode();
    }
  }
}
