package org.jetbrains.jps.android;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackagingStateStorage implements StorageOwner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidPackagingStateStorage");

  private final File myFile;

  private AndroidPackagingStateStorage(@NotNull File file) {
    myFile = file;
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
  }

  @Override
  public void clean() throws IOException {
    FileUtil.delete(myFile);
  }

  @Override
  public void close() throws IOException {
  }

  @Nullable
  public MyState read() {
    try {
      final DataInputStream input = new DataInputStream(new FileInputStream(myFile));
      try {
        final boolean release = input.readBoolean();
        return new MyState(release);
      }
      finally {
        input.close();
      }
    }
    catch (FileNotFoundException ignored) {
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  public void saveState(@NotNull MyState state) {
    FileUtil.createParentDirs(myFile);
    try {
      final DataOutputStream output = new DataOutputStream(new FileOutputStream(myFile));
      try {
        output.writeBoolean(state.myRelease);
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  public static class MyState {
    private final boolean myRelease;

    public MyState(boolean release) {
      myRelease = release;
    }

    public boolean isRelease() {
      return myRelease;
    }
  }

  public static class Provider extends StorageProvider<AndroidPackagingStateStorage> {
    public static final Provider INSTANCE = new Provider();

    private Provider() {
    }

    @NotNull
    @Override
    public AndroidPackagingStateStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidPackagingStateStorage(new File(targetDataDir, "android_packaging_options" + File.separator + "data"));
    }
  }
}
