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
public class AndroidGenSourcesCopyingStorage implements StorageOwner {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidGenSourcesCopyingStorage");

  public static final StorageProvider<AndroidGenSourcesCopyingStorage> PROVIDER = new StorageProvider<AndroidGenSourcesCopyingStorage>() {
    @NotNull
    @Override
    public AndroidGenSourcesCopyingStorage createStorage(File targetDataDir) throws IOException {
      return new AndroidGenSourcesCopyingStorage(new File(targetDataDir, "gen_sources_copying" + File.separator + "data"));
    }
  };

  private final File myFile;

  private AndroidGenSourcesCopyingStorage(@NotNull File file) {
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
  public AndroidFileSetState read() {
    try {
      final DataInputStream input = new DataInputStream(new FileInputStream(myFile));
      try {
        return new AndroidFileSetState(input);
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

  public void saveState(@NotNull AndroidFileSetState state) {
    FileUtil.createParentDirs(myFile);
    try {
      final DataOutputStream output = new DataOutputStream(new FileOutputStream(myFile));
      try {
        state.save(output);
      }
      finally {
        output.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }
}
