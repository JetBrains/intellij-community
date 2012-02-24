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

  @NonNls private static final String ANDROID_CLASSES_AND_JARS_STORAGE_DIR = "android_classes_and_jars";
  @NonNls private static final String CLASSES_AND_JARS_STORAGE = "classes_and_jars";

  public AndroidClassesAndJarsStateStorage(@NotNull File dataStorageRoot) throws IOException {
    super(getStorageFile(dataStorageRoot), new EnumeratorStringDescriptor(), new MyDataExternalizer());
  }

  @NotNull
  private static File getStorageFile(@NotNull File dataStorageRoot) {
    return new File(new File(dataStorageRoot, ANDROID_CLASSES_AND_JARS_STORAGE_DIR), CLASSES_AND_JARS_STORAGE);
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
