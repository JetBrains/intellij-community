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
public class AndroidClassesAndJarsStateStorage extends AbstractStateStorage<String, AndroidClassesAndJarsState> {

  @NonNls private static final String CLASSES_AND_JARS_STORAGE = "classes_and_jars";

  public AndroidClassesAndJarsStateStorage(@NotNull File dataStorageRoot, @NotNull String suffix) throws IOException {
    super(getStorageFile(dataStorageRoot, suffix), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  @NotNull
  private static File getStorageFile(@NotNull File dataStorageRoot, @NotNull String suffix) {
    return new File(new File(new File(dataStorageRoot, AndroidJpsUtil.ANDROID_STORAGE_DIR), CLASSES_AND_JARS_STORAGE + suffix),
                    CLASSES_AND_JARS_STORAGE);
  }

  private static class MyDataExternalizer implements DataExternalizer<AndroidClassesAndJarsState> {

    @Override
    public void save(DataOutput out, AndroidClassesAndJarsState value) throws IOException {
      value.save(out);
    }

    @Override
    public AndroidClassesAndJarsState read(DataInput in) throws IOException {
      return new AndroidClassesAndJarsState(in);
    }
  }
}
